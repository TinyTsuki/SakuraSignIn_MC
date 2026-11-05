package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.internal.client.ui.page.FormUiSession;
import xin.vanilla.sakura.client.ui.PersonalDateUiSession;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.test.BaniraTestPlatform;
import xin.vanilla.sakura.test.UnboundConfigPlatform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class PersonalDateInputFormTest {
    @org.junit.Rule
    public final xin.vanilla.sakura.test.NativeClientRule nativeClient = new xin.vanilla.sakura.test.NativeClientRule();

    @Rule
    public final UnboundConfigPlatform platform = new UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        xin.vanilla.sakura.test.NativeBootstrap.initialize();
        BaniraTestPlatform.install();
    }

    @Test
    public void annualWidgetEnforcesTheOriginalFormatWithoutCalendarRangePolicy() throws Exception {
        InputFormScreen form = form(PersonalDateRecurrence.YEARLY, slot(), () -> true, new AtomicInteger());
        InputFormScreen.Widget widget = form.uiSession().args().getWidgets().get(0);
        assertEquals("[0-9]{1,2}/[0-9]{1,3}", widget.regex());
        for (String invalid : List.of("10/", "6", "1/2/3", "-1/2", "100/1", "1/1000")) {
            assertFalse("Annual format accepted " + invalid, validate(widget, invalid).isEmpty());
        }
        for (String valid : List.of("10/9", "0/0", "99/999", "01/001")) {
            assertEquals("Format validation must not impose Gregorian ranges: " + valid, "", validate(widget, valid));
        }
    }

    @Test
    public void monthlyWidgetRejectsSlashesAndKeepsThreeDigitDays() throws Exception {
        InputFormScreen form = form(PersonalDateRecurrence.MONTHLY, slot(), () -> true, new AtomicInteger());
        InputFormScreen.Widget widget = form.uiSession().args().getWidgets().get(0);
        assertEquals("[0-9]{1,3}", widget.regex());
        for (String invalid : List.of("6/", "1/2", "-1", "1000", "abc")) {
            assertFalse("Monthly format accepted " + invalid, validate(widget, invalid).isEmpty());
        }
        for (String valid : List.of("0", "6", "999", "001")) assertEquals("", validate(widget, valid));
    }

    @Test
    public void annualMalformedSubmissionCanBeCorrectedOnceInTheSameForm() throws Exception {
        correctedSubmission(PersonalDateRecurrence.YEARLY, "10/", "10/9", 10, 9);
    }

    @Test
    public void monthlySlashSubmissionCanBeCorrectedOnceInTheSameForm() throws Exception {
        correctedSubmission(PersonalDateRecurrence.MONTHLY, "6/", "6", 0, 6);
    }

    @Test
    public void annualOverflowSubmissionCanBeCorrectedInTheSameForm() throws Exception {
        correctedSubmission(PersonalDateRecurrence.YEARLY, "10/2147483648", "10/9", 10, 9);
    }

    @Test
    public void rawAnnualMissingDayDoesNotMutateOrConsumeTheCallback() throws Exception {
        correctedCallback(PersonalDateRecurrence.YEARLY, "10/", "10/9", 10, 9);
    }

    @Test
    public void rawMonthlySlashDoesNotMutateOrConsumeTheCallback() throws Exception {
        correctedCallback(PersonalDateRecurrence.MONTHLY, "6/", "6", 0, 6);
    }

    @Test
    public void rawAnnualDayOverflowDoesNotPartiallyWriteTheMonth() throws Exception {
        correctedCallback(PersonalDateRecurrence.YEARLY, "10/2147483648", "10/9", 10, 9);
    }

    @Test
    public void rawAnnualMonthOverflowDoesNotConsumeTheCallback() throws Exception {
        correctedCallback(PersonalDateRecurrence.YEARLY, "2147483648/9", "10/9", 10, 9);
    }

    @Test
    public void rawMonthlyOverflowDoesNotClearTheMonthOrConsumeTheCallback() throws Exception {
        correctedCallback(PersonalDateRecurrence.MONTHLY, "2147483648", "6", 0, 6);
    }

    @Test
    public void acceptedDateFormatsDoNotAcquireGregorianRangeChecks() throws Exception {
        for (PersonalDateRecurrence recurrence : PersonalDateRecurrence.values()) {
            PlayerPersonalDateSlot slot = slot();
            AtomicInteger updates = new AtomicInteger();
            InputFormScreen form = form(recurrence, slot, () -> true, updates);
            form.uiSession().draft("date", recurrence == PersonalDateRecurrence.MONTHLY ? "999" : "99/999");
            assertTrue(form.uiSession().submit());
            assertApplied(slot, updates, recurrence == PersonalDateRecurrence.MONTHLY ? 0 : 99, 999);
        }
    }

    @Test
    public void callbackFromTheOriginalConnectionCannotEditAfterReconnect() throws Exception {
        Object original = new Object();
        AtomicReference<Object> current = new AtomicReference<>(original);
        PersonalDateUiSession parent = parent(() -> current.get() == original);
        PlayerPersonalDateSlot slot = slot();
        CompoundTag before = slot.serializeNBT();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen form = form(PersonalDateRecurrence.YEARLY, slot, childGate(parent), updates);
        current.set(new Object());
        form.uiSession().args().getCallback().accept(results("10/9"));
        assertEquals(before, slot.serializeNBT());
        assertEquals(0, updates.get());
    }

    @Test
    public void closingAndReopeningAParentOnTheSameConnectionCannotReviveItsOldChild() throws Exception {
        PersonalDateUiSession original = parent(() -> true);
        PlayerPersonalDateSlot slot = slot();
        CompoundTag before = slot.serializeNBT();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen stale = form(PersonalDateRecurrence.YEARLY, slot, childGate(original), updates);
        original.close(UiCloseReason.USER_CLOSE);
        PersonalDateUiSession reopened = parent(() -> true);
        assertTrue(childGate(reopened).getAsBoolean());
        assertFalse(childGate(original).getAsBoolean());
        stale.uiSession().args().getCallback().accept(results("10/9"));
        assertEquals(before, slot.serializeNBT());
        assertEquals(0, updates.get());
        InputFormScreen fresh = form(PersonalDateRecurrence.YEARLY, slot, childGate(reopened), updates);
        fresh.uiSession().draft("date", "11/8");
        assertTrue(fresh.uiSession().submit());
        assertApplied(slot, updates, 11, 8);
    }

    @Test
    public void aDetachedParentStillAcceptsItsActiveChildResult() throws Exception {
        PersonalDateUiSession parent = parent(() -> true);
        PlayerPersonalDateSlot slot = slot();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen form = form(PersonalDateRecurrence.YEARLY, slot, childGate(parent), updates);
        parent.detach(UiDetachReason.SWITCH);
        assertTrue(childGate(parent).getAsBoolean());
        form.uiSession().draft("date", "10/9");
        assertTrue(form.uiSession().submit());
        assertApplied(slot, updates, 10, 9);
    }

    @Test
    public void invalidDraftAndCorrectionSurviveFormAndParentSuspension() throws Exception {
        PersonalDateUiSession parent = parent(() -> true);
        PlayerPersonalDateSlot slot = slot();
        CompoundTag before = slot.serializeNBT();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen form = form(PersonalDateRecurrence.YEARLY, slot, childGate(parent), updates);
        FormUiSession session = form.uiSession();
        session.draft("date", "10/");
        assertFalse(session.submit());
        for (UiDetachReason reason : List.of(UiDetachReason.SWITCH, UiDetachReason.RESIZE, UiDetachReason.RELOAD)) {
            session.detach(reason);
            parent.detach(reason);
            InputFormScreen resumed = new InputFormScreen(session);
            assertSame(session, resumed.uiSession());
            assertEquals("10/", resumed.uiSession().draft("date"));
            assertFalse(session.finished());
            assertEquals(before, slot.serializeNBT());
        }
        session.draft("date", "10/9");
        assertTrue(session.submit());
        assertFalse(session.submit());
        assertApplied(slot, updates, 10, 9);
    }

    private static void correctedSubmission(PersonalDateRecurrence recurrence, String invalid, String valid,
                                            int month, int day) throws Exception {
        PlayerPersonalDateSlot slot = slot();
        CompoundTag before = slot.serializeNBT();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen form = form(recurrence, slot, () -> true, updates);
        FormUiSession session = form.uiSession();
        session.draft("date", invalid);
        assertFalse("Malformed input must not finish the form", session.submit());
        assertFalse(session.finished());
        assertEquals(before, slot.serializeNBT());
        assertEquals(0, updates.get());
        session.draft("date", valid);
        assertTrue("A corrected value in the same session must apply", session.submit());
        assertFalse(session.submit());
        form.uiSession().args().getCallback().accept(results(recurrence == PersonalDateRecurrence.MONTHLY ? "11" : "11/11"));
        assertApplied(slot, updates, month, day);
    }

    private static void correctedCallback(PersonalDateRecurrence recurrence, String invalid, String valid,
                                          int month, int day) throws Exception {
        PlayerPersonalDateSlot slot = slot();
        CompoundTag before = slot.serializeNBT();
        AtomicInteger updates = new AtomicInteger();
        InputFormScreen form = form(recurrence, slot, () -> true, updates);
        InputFormScreen.Args args = form.uiSession().args();
        assertThrows(IllegalArgumentException.class, () -> args.getCallback().accept(results(invalid)));
        assertEquals(before, slot.serializeNBT());
        assertEquals(0, updates.get());
        args.getCallback().accept(results(valid));
        args.getCallback().accept(results(recurrence == PersonalDateRecurrence.MONTHLY ? "11" : "11/11"));
        assertApplied(slot, updates, month, day);
    }

    private static String validate(InputFormScreen.Widget widget, String value) {
        return widget.validator().apply(results(value).curIndex(0).curName("date"));
    }

    private static InputFormScreen.Results results(String value) {
        return new InputFormScreen.Results().value("date", 0, value);
    }

    private static PlayerPersonalDateSlot slot() {
        return new PlayerPersonalDateSlot("birthday", 0, CalendarIds.GREGORIAN, 2, 3, "YEARLY:2025");
    }

    private static void assertApplied(PlayerPersonalDateSlot slot, AtomicInteger updates, int month, int day) {
        assertEquals(month, slot.getMonth());
        assertEquals(day, slot.getDay());
        assertEquals(1, updates.get());
        assertEquals("birthday", slot.getPresetId());
        assertEquals(0, slot.getSlotIndex());
        assertEquals(CalendarIds.GREGORIAN, slot.getCalendarId());
        assertEquals("YEARLY:2025", slot.getLastClaimedOccurrenceKey());
    }

    private static PersonalDateUiSession parent(BooleanSupplier connected) {
        return new PersonalDateUiSession(new PersonalDateUiSession.Port() {
            public List<PersonalDateUiSession.Entry> entries() {
                return Collections.emptyList();
            }

            public void cycle(String key) {
                throw new AssertionError("Unexpected calendar change");
            }

            public void edit(String key) {
                throw new AssertionError("Unexpected nested edit");
            }

            public void save() {
                throw new AssertionError("Date input must not upload");
            }

            public void commonConfig() {
                throw new AssertionError("Unexpected config navigation");
            }
        }, connected);
    }

    private static BooleanSupplier childGate(PersonalDateUiSession parent) {
        final Method method;
        try {
            method = PersonalDateUiSession.class.getMethod("acceptsChildResult");
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Missing parent lifetime gate for child results", missing);
        }
        return () -> {
            try {
                return (Boolean) method.invoke(parent);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot evaluate parent lifetime", failure);
            }
        };
    }

    private static InputFormScreen form(PersonalDateRecurrence recurrence, PlayerPersonalDateSlot slot,
                                        BooleanSupplier acceptsResult, AtomicInteger updates) throws Exception {
        final Method factory;
        try {
            factory = Class.forName("xin.vanilla.sakura.client.gui.PersonalDateInputForm").getMethod("create",
                    Screen.class, PersonalDatePreset.class, PlayerPersonalDateSlot.class, BooleanSupplier.class, Runnable.class);
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            throw new AssertionError("Missing validated personal-date input factory", missing);
        }
        PersonalDatePreset preset = new PersonalDatePreset("birthday", "Birthday", recurrence,
                Collections.singletonList(CalendarIds.GREGORIAN), 1, PersonalDateDeliveryMode.SIGN_IN,
                0, 0, new RewardList());
        Screen parent = new Screen(Component.literal("parent")) {
        };
        try {
            Object screen = factory.invoke(null, parent, preset, slot, acceptsResult, (Runnable) updates::incrementAndGet);
            assertTrue("Date factory must return the actual shared input form", screen instanceof InputFormScreen);
            return (InputFormScreen) screen;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("Date factory failed", cause);
        }
    }
}
