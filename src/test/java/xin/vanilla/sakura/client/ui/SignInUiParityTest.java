package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.api.client.ui.UiPointer;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SignInUiParityTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        xin.vanilla.sakura.test.NativeBootstrap.initialize();
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
    }

    static Component text(String text) {
        return SakuraComponent.get().literal(text);
    }

    static class Port implements SignInUiSession.Port {
        long revision;
        int moves, reads, refreshes, themes, keys, persists, dismissals, selectedTheme = -1;
        boolean tips, special, empty;
        final List<Integer> requests = new ArrayList<>();
        final Texture texture = Texture.of(net.minecraft.resources.Identifier.fromNamespaceAndPath("sakura_sign_in", "calendar.png"), 64, 64).uWidth(8).vHeight(8);

        public long revision() {
            return revision;
        }

        public void refresh() {
            refreshes++;
        }

        public List<SignInUiSession.Cell> cells() {
            reads++;
            if (empty) return List.of();
            return List.of(new SignInUiSession.Cell(20261009, text("9"), text("2026-10-09\nItem reward"), texture),
                    new SignInUiSession.Cell(20261008, text("8"), text("Repair"), texture),
                    new SignInUiSession.Cell(20261007, text("7"), text("Claim"), texture));
        }

        public Component title() {
            return text("October 2026");
        }

        public Component info() {
            return text("Cards 3");
        }

        public List<Component> weekdays() {
            return List.of(text("Mon"), text("Tue"), text("Wed"), text("Thu"), text("Fri"), text("Sat"), text("Sun"));
        }

        public Component tips() {
            return text("Tips");
        }

        public boolean openingTips() {
            return tips;
        }

        public void dismissTips(boolean persist) {
            tips = false;
            dismissals++;
            if (persist) persists++;
            revision++;
        }

        public void move(int months, int years) {
            moves += months + years * 12;
            revision++;
        }

        public void activate(int date) {
            requests.add(date);
            revision++;
        }

        public void theme(int index, boolean special) {
            themes++;
            selectedTheme = index;
            this.special = special;
            revision++;
        }

        public Texture themeIcon(int index) {
            return texture;
        }

        public boolean key(int code, int scan, boolean pressed) {
            keys++;
            return true;
        }
    }

    @Test
    public void explicitCalendarActionsKeepDateIdentityAndDoNotRepeatOnHandoff() {
        Port port = new Port();
        SignInUiSession session = new SignInUiSession(port, () -> true);
        RecordingUiView first = new RecordingUiView();
        session.bind(first);
        Runnable stale = first.actions.get("day0");
        first.actions.get("day0").run();
        first.actions.get("day1").run();
        first.actions.get("day2").run();
        assertEquals(List.of(20261009, 20261008, 20261007), port.requests);
        first.actions.get("previous-month").run();
        first.actions.get("next-year").run();
        assertEquals(11, port.moves);
        session.detach(UiDetachReason.SWITCH);
        session.bind(new RecordingUiView());
        stale.run();
        assertEquals(3, port.requests.size());
        assertEquals(11, port.moves);
        session.detach(UiDetachReason.RESIZE);
        session.bind(new RecordingUiView());
        session.detach(UiDetachReason.RELOAD);
        session.bind(new RecordingUiView());
        assertEquals(3, port.requests.size());
    }

    @Test
    public void aShorterMonthClearsEveryUnusedStatusSprite() {
        Port port = new Port();
        SignInUiSession session = new SignInUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertNotNull(view.icons.get("status0"));
        port.empty = true;
        port.revision++;
        session.tick();
        assertNull("Old month status must not survive a shorter calendar", view.icons.get("status0"));
    }

    @Test
    public void nativeFallbackAndDisconnectCannotSubmitOrReceiveStaleInput() {
        Port port = new Port();
        boolean[] connected = {true};
        SignInUiSession session = new SignInUiSession(port, () -> connected[0]);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        Runnable saved = view.actions.get("day0");
        connected[0] = false;
        saved.run();
        assertTrue(port.requests.isEmpty());
        connected[0] = true;
        session.detach(UiDetachReason.FALLBACK);
        RecordingUiView nativeView = new RecordingUiView();
        nativeView.nativeView = true;
        session.bind(nativeView);
        saved.run();
        assertTrue(nativeView.actions.isEmpty());
        assertTrue(port.requests.isEmpty());
        session.close(UiCloseReason.DISCONNECT);
        session.bind(new RecordingUiView());
        assertFalse(session.key(65, 0, 0, true));
    }

    @Test
    public void fiveThemesRightClickVariantTipsAndKeysUseOriginalPort() {
        Port port = new Port();
        port.tips = true;
        SignInUiSession session = new SignInUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        view.actions.get("day0").run();
        assertTrue(port.requests.isEmpty());
        view.actions.get("tips-confirm").run();
        assertEquals(1, port.dismissals);
        assertEquals(0, port.persists);
        for (int index = 0; index < 5; index++) {
            assertNotNull(view.icons.get("theme" + index));
            view.actions.get("theme" + index).run();
        }
        view.pointers.get("theme4").accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        view.pointers.get("theme4").accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(6, port.themes);
        assertTrue(port.special);
        assertTrue(session.key(263, 0, 0, false));
        assertEquals(1, port.keys);
        int reads = port.reads;
        for (int tick = 0; tick < 100; tick++) session.tick();
        assertEquals(reads, port.reads);
        port.tips = true;
        port.revision++;
        session.tick();
        view.actions.get("tips-disable").run();
        assertEquals(1, port.persists);
    }

    @Test
    public void globalSecondaryReleaseOnlyCommitsThePressedThemeOnceInsideItsBounds() {
        Port port = new Port();
        SignInUiSession session = new SignInUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        for (var pointer : view.pointers.values()) pointer.accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals("An unrelated global release must not select any theme", 0, port.themes);
        view.pointers.get("theme2").accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        for (var pointer : view.pointers.values()) pointer.accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(1, port.themes);
        assertEquals(2, port.selectedTheme);
        assertTrue(port.special);
        view.pointers.get("theme4").accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        for (var pointer : view.pointers.values()) pointer.accept(new UiPointer(UiPointer.Kind.UP, -1, -1, 1));
        assertEquals(1, port.themes);
        var stale = view.pointers.get("theme3");
        stale.accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        session.detach(UiDetachReason.SWITCH);
        session.bind(new RecordingUiView());
        stale.accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(1, port.themes);
    }

    @Test
    public void signInHasSharedSessionWithoutDuplicatingRewardService() {
        try {
            Class<?> type = Class.forName("xin.vanilla.sakura.client.ui.SignInUiSession");
            assertTrue(xin.vanilla.banira.api.client.ui.UiPageSession.class.isAssignableFrom(type));
            type.getMethod("key", int.class, int.class, int.class, boolean.class);
        } catch (ReflectiveOperationException missing) {
            fail("Missing shared sign-in session: " + missing);
        }
    }
}
