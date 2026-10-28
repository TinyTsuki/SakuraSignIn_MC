package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiPointer;

import static org.junit.Assert.*;

public class ApprovedSignInPagesTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void calendarHasSharedBrandAndRealHoverDetailsWithoutClaiming() {
        var port = new SignInUiParityTest.Port();
        var view = new RecordingUiView();
        var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        assertNotNull("Shared mod icon must be bound", view.icons.get("brand"));
        assertNotNull("Date hover must select details without a claim", view.pointers.get("day1"));
        view.pointers.get("day1").accept(new UiPointer(UiPointer.Kind.MOVE, 5, 5, -1));
        assertTrue(port.requests.isEmpty());
        assertEquals("20261008", view.state.selectedId());
        assertTrue(view.texts.get("detail_date").toString().contains("10 / 08"));
        view.actions.get("day1").run();
        assertEquals(java.util.List.of(20261008), port.requests);
    }

    @Test
    public void dateSettingsHaveSharedConfigNavigationAndSearchClear() {
        var port = new PersonalDateUiParityTest.Port();
        var view = new RecordingUiView();
        var session = new PersonalDateUiSession(port, () -> true);
        session.bind(view);
        assertNotNull("Shared mod icon must be bound", view.icons.get("brand"));
        assertNotNull("Preset sidebar must use real entries", view.providers.get("categories"));
        assertEquals(2, view.providers.get("categories").size());
        assertNotNull(view.actions.get("clear_search"));
        assertNotNull("Shared search needs its translated placeholder", view.texts.get("search_hint"));
        assertFalse(view.texts.get("search_hint").toString().contains("word.banira_codex."));
        view.drafts.get("search").accept("Lunar");
        view.actions.get("clear_search").run();
        assertEquals("", session.query());
        assertEquals(0, port.saves);
    }

    @Test
    public void calendarDetailsDoNotAlsoOpenTheLegacyRewardPopup() {
        var view = new RecordingUiView();
        new SignInUiSession(new SignInUiParityTest.Port(), () -> true).bind(view);
        assertEquals("The persistent detail panel replaces the duplicate AUI popup", "", view.tooltips.get("day0").toString());
    }

    @Test
    public void nativeFallbackStillDoesNotBindOrSubmitAnyUiAction() {
        var port = new PersonalDateUiParityTest.Port();
        var view = new RecordingUiView();
        view.nativeView = true;
        new PersonalDateUiSession(port, () -> true).bind(view);
        assertTrue(view.actions.isEmpty());
        assertEquals(0, port.saves);
    }

    @Test
    public void stationaryCursorImmediatelyLosesRevokedRewardDetails() {
        boolean[] details = {true};
        var port = new SignInUiParityTest.Port() {
            public long detailRevision() {
                return details[0] ? 1 : 0;
            }

            public boolean detailsVisible(int date) {
                return details[0];
            }

            public java.util.List<SignInUiSession.RewardLine> rewards(int date) {
                return java.util.List.of(new SignInUiSession.RewardLine(SignInUiParityTest.text("Secret reward"), SignInUiParityTest.text("50%"), null));
            }
        };
        var view = new RecordingUiView();
        var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        assertEquals(1, view.rows.size());
        details[0] = false;
        session.tick();
        assertEquals("Permission loss must clear existing rows without movement", 0, view.rows.size());
        assertTrue(port.requests.isEmpty());
    }

    @Test
    public void nativeFirstEditsAreComparedWithThePreEditBaseline() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.sakura.client.ui.PersonalDateDraftState");
        } catch (ClassNotFoundException absent) {
            fail("Missing renderer-independent date draft baseline");
            return;
        }
        var slot = new xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot("birthday", 0, "minecraft:gregorian", 1, 1, "");
        var slots = java.util.List.of(slot);
        var draft = type.getConstructor(java.util.List.class).newInstance(slots);
        slot.setDay(9);
        assertEquals(true, type.getMethod("dirty", java.util.List.class).invoke(draft, slots));
        type.getMethod("submitted", java.util.List.class).invoke(draft, slots);
        assertEquals(false, type.getMethod("dirty", java.util.List.class).invoke(draft, slots));
    }
}
