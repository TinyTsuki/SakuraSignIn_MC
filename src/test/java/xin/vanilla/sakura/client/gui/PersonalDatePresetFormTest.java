package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.Bootstrap;
import org.junit.*;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.*;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PersonalDatePresetFormTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); BaniraTestPlatform.install(); }
    @Before public void prepare() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(CalendarIds.GREGORIAN, ""); names.put(CalendarIds.CHINESE_LUNAR, "");
        SakuraClientState.setCalendarNames(names);
        RewardConfigManager.setRewardConfig(new RewardConfig());
    }
    @After public void cleanup() { SakuraClientState.clearSession(); RewardConfigManager.setRewardConfig(new RewardConfig()); }

    @Test public void calendarValidationAcceptsStructuredSelectionAndRejectsEmptySelection() throws Exception {
        InputFormScreen.Widget widget = calendars(form(null, new AtomicReference<>()));
        try {
            assertEquals("", widget.validator().apply(new InputFormScreen.Results()
                    .values("calendars", 3, Collections.singletonList(CalendarIds.GREGORIAN)).curName("calendars")));
            assertFalse(widget.validator().apply(new InputFormScreen.Results()
                    .values("calendars", 3, Collections.emptyList()).curName("calendars")).isEmpty());
        } catch (IllegalStateException error) { fail("Calendar validator must read structured values: " + error.getMessage()); }
    }

    @Test public void submissionPreservesSelectedCalendarsAndExistingRewards() throws Exception {
        PersonalDatePreset existing = preset();
        AtomicReference<PersonalDatePreset> submitted = new AtomicReference<>();
        InputFormScreen.Args args = form(existing, submitted);
        InputFormScreen.Results results = new InputFormScreen.Results();
        int index = 0;
        for (InputFormScreen.Widget widget : args.getWidgets()) {
            if (widget.dropdownMultiSelect()) results.values(widget.name(), index, existing.getCalendarIds());
            else results.value(widget.name(), index, widget.defaultValue());
            index++;
        }
        try { args.getCallback().accept(results); }
        catch (IllegalStateException error) { fail("Submission must read structured calendars: " + error.getMessage()); }
        assertNotNull(submitted.get());
        assertEquals(existing.getCalendarIds(), submitted.get().getCalendarIds());
        assertSame(existing.getRewards(), submitted.get().getRewards());
        assertEquals(existing.getId(), submitted.get().getId());
    }

    @Test public void editingRestoresMultipleCalendarsAsIndependentDefaultValues() throws Exception {
        PersonalDatePreset existing = preset();
        assertEquals(existing.getCalendarIds(), calendars(form(existing, new AtomicReference<>())).dropdownDefaultValues());
    }

    @Test public void scalarResultsCannotSilentlyFlattenMultiselectValues() {
        InputFormScreen.Results results = new InputFormScreen.Results().values("calendars", 0,
                Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR));
        assertThrows(IllegalStateException.class, () -> results.value("calendars"));
    }

    private static PersonalDatePreset preset() {
        return new PersonalDatePreset("example", "Example", PersonalDateRecurrence.YEARLY,
                Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR), 1,
                PersonalDateDeliveryMode.SIGN_IN, 0, 0, new RewardList());
    }
    private static InputFormScreen.Args form(PersonalDatePreset existing, AtomicReference<PersonalDatePreset> submitted) throws Exception {
        Screen parent = new Screen(new TextComponent("parent")) {};
        Screen screen = PersonalDatePresetForm.create(parent, existing, submitted::set);
        Field field = InputFormScreen.class.getDeclaredField("args"); field.setAccessible(true);
        return (InputFormScreen.Args) field.get(screen);
    }
    private static InputFormScreen.Widget calendars(InputFormScreen.Args args) {
        return args.getWidgets().stream().filter(widget -> widget.name().equals("calendars")).findFirst().get();
    }
}
