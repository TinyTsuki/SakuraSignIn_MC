package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;

import java.util.List;

import static org.junit.Assert.*;

public class PersonalDateUiParityTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    static final class Port implements PersonalDateUiSession.Port {
        int cycles, edits, saves, common;

        public List<PersonalDateUiSession.Entry> entries() {
            return com.google.common.collect.ImmutableList.of(
                    new PersonalDateUiSession.Entry("birthday", "Birthday", "", "", "birthday", true),
                    new PersonalDateUiSession.Entry("birthday:0", "Birthday 1", "Gregorian", "minecraft:gregorian", "10/9", "birthday", false),
                    new PersonalDateUiSession.Entry("birthday:1", "Birthday 2", "Lunar", "minecraft:chinese_lunar", "8/15", "birthday", false));
        }

        public void cycle(String key) {
            assertEquals("birthday:0", key);
            cycles++;
        }

        public void edit(String key) {
            assertEquals("birthday:1", key);
            edits++;
        }

        public void save() {
            saves++;
        }

        public void commonConfig() {
            common++;
        }
    }

    @Test
    public void draftSectionsAndSlotIdentitySurviveReloadWithoutSaving() {
        Port port = new Port();
        PersonalDateUiSession session = new PersonalDateUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(120, view.limit);
        assertEquals(3, view.rows.size());
        RecordingUiView slot = new RecordingUiView();
        view.rows.bind(slot, 1);
        Runnable stale = slot.actions.get("open");
        slot.actions.get("open").run();
        assertEquals(1, port.cycles);
        RecordingUiView date = new RecordingUiView();
        view.rows.bind(date, 2);
        date.actions.get("apply").run();
        assertEquals(1, port.edits);
        session.expanded("birthday", false);
        session.query("Lunar");
        session.detach(UiDetachReason.RELOAD);
        RecordingUiView next = new RecordingUiView();
        session.bind(next);
        stale.run();
        assertEquals(1, port.cycles);
        assertEquals(0, port.saves);
        assertEquals("Lunar", session.query());
        assertFalse(session.expanded("birthday"));
        assertEquals(2, next.rows.size());
        assertEquals("birthday:1", next.rows.key(1));
        next.actions.get("save").run();
        assertEquals(1, port.saves);
    }

    @Test
    public void disconnectedAndDetachedActionsCannotUpdateDatesOrSave() {
        Port port = new Port();
        boolean[] connected = {true};
        PersonalDateUiSession session = new PersonalDateUiSession(port, () -> connected[0]);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        Runnable save = view.actions.get("save");
        connected[0] = false;
        save.run();
        assertEquals(0, port.saves);
        session.close(UiCloseReason.DISCONNECT);
        connected[0] = true;
        save.run();
        assertEquals(0, port.saves);
    }

    @Test
    public void calendarIdentifierAndTranslatedNameReachTheSameSlot() {
        Port port = new Port();
        PersonalDateUiSession session = new PersonalDateUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.query("minecraft:chinese_lunar");
        session.bind(view);
        assertEquals("Raw calendar ID must remain searchable", 2, view.rows.size());
        assertEquals("birthday:1", view.rows.key(1));
        view.drafts.get("search").accept("Lunar");
        assertEquals(2, view.rows.size());
        assertEquals("birthday:1", view.rows.key(1));
        assertEquals(0, port.saves);
    }

    @Test
    public void personalDatesHaveSharedSessionWithNativeDraftHandoff() {
        try {
            Class<?> type = Class.forName("xin.vanilla.sakura.client.ui.PersonalDateUiSession");
            assertTrue(xin.vanilla.banira.api.client.ui.UiPageSession.class.isAssignableFrom(type));
            type.getMethod("requestClose");
        } catch (ReflectiveOperationException missing) {
            fail("Missing personal-date session: " + missing);
        }
    }
}
