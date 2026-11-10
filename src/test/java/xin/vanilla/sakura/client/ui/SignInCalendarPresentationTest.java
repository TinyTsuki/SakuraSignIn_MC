package xin.vanilla.sakura.client.ui;

import org.junit.*;

import java.time.LocalDate;
import java.util.*;
import java.nio.charset.StandardCharsets;

import xin.vanilla.banira.api.client.ui.*;

import static org.junit.Assert.*;

public class SignInCalendarPresentationTest {
    @org.junit.Rule
    public final xin.vanilla.sakura.test.NativeClientRule nativeClient = new xin.vanilla.sakura.test.NativeClientRule();

    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @SuppressWarnings("unchecked")
    private List<SignInUiSession.Cell> grid(LocalDate month, int weekStart, List<SignInUiSession.Cell> cells) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.sakura.client.ui.SignInCalendarGrid");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Complete AUI calendar required", missing);
        }
        return (List<SignInUiSession.Cell>) type.getMethod("complete", LocalDate.class, int.class, List.class).invoke(null, month, weekStart, cells);
    }

    @Test
    public void calendarOnlyExplainsSignedAndClaimedMarksAndUnsignedDatesRemainActionable() throws Exception {


        var port = new SignInUiParityTest.Port() {
            public boolean marked(int date) {
                return date != 20261009;
            }
        };
        var view = new RecordingUiView();
        var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        assertNull(view.icons.get("status0"));
        assertNotNull(view.icons.get("status1"));
        assertNotNull(view.icons.get("status2"));
        assertFalse(view.icons.containsKey("legend_not_signed"));
        assertNotNull(view.icons.get("legend_unclaimed"));
        assertNotNull(view.icons.get("legend_claimed"));
        view.actions.get("day0").run();
        assertEquals(List.of(20261009), port.requests);
    }

    @Test
    public void sixFullWeeksIncludeBothAdjacentMonthsAndRetainExistingRewards() throws Exception {
        var original = new SignInUiSession.Cell(20261009, SignInUiParityTest.text("9"), SignInUiParityTest.text("Reward"), new SignInUiParityTest.Port().texture);
        var cells = grid(LocalDate.of(2026, 10, 1), 7, List.of(original));
        assertEquals(42, cells.size());
        assertEquals(20260927, cells.get(0).date());
        assertEquals(20261107, cells.get(41).date());
        var retained = cells.stream().filter(cell -> cell.date() == 20261009).findFirst().orElseThrow();
        assertSame(original.icon(), retained.icon());
        assertSame(original.tooltip(), retained.tooltip());
        assertEquals(true, retained.getClass().getMethod("currentMonth").invoke(retained));
        assertEquals(false, cells.get(0).getClass().getMethod("currentMonth").invoke(cells.get(0)));
        assertEquals(false, cells.get(41).getClass().getMethod("actionable").invoke(cells.get(41)));
    }

    @Test
    public void leapMonthsAndYearBoundariesHaveContinuousDates() throws Exception {
        for (LocalDate month : List.of(LocalDate.of(2024, 2, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1))) {
            for (int weekStart = 1; weekStart <= 7; weekStart++) {
                var cells = grid(month, weekStart, List.of());
                assertEquals(42, cells.size());
                Set<Integer> dates = new HashSet<>();
                LocalDate previous = null;
                for (var cell : cells) {
                    assertTrue(dates.add(cell.date()));
                    LocalDate date = LocalDate.of(cell.date() / 10000, cell.date() / 100 % 100, cell.date() % 100);
                    if (previous != null) assertEquals(previous.plusDays(1), date);
                    previous = date;
                }
                long primary = cells.stream().filter(cell -> cell.date() / 100 == month.getYear() * 100 + month.getMonthValue()).count();
                assertEquals(month.lengthOfMonth(), primary);
            }
        }
    }

    @Test
    public void fillerDatesHaveNoClaimActionAndAllFortyTwoCellsAreBound() throws Exception {
        var cells = grid(LocalDate.of(2026, 10, 1), 7, List.of());
        var port = new SignInUiParityTest.Port() {
            public List<SignInUiSession.Cell> cells() {
                return cells;
            }
        };
        var view = new RecordingUiView();
        new SignInUiSession(port, () -> true).bind(view);
        String selected = view.state.selectedId();
        view.pointers.get("day41").accept(new UiPointer(UiPointer.Kind.MOVE, 5, 5, -1));
        assertEquals(selected, view.state.selectedId());
        assertNotNull(view.actions.get("day41"));
        view.actions.get("day41").run();
        assertTrue(port.requests.isEmpty());
        assertEquals("7", view.texts.get("day41_adjacent").toString());
        assertEquals(false, view.enabled.get("day41_current"));
        assertEquals(true, view.enabled.get("day41_adjacent"));
    }


    @Test
    public void ruleSidebarIconIsVisibleWithoutOpeningMore() {
        var view = new RecordingUiView();
        new RewardEditorUiSession(new RewardEditorUiParityTest.Port(2), () -> true).bind(view);
        assertNotNull("Rule sidebar toggle must be visible on the toolbar", view.icons.get("sidebar_icon"));
    }

    @Test
    public void adjacentDayNumbersHaveALighterStyleWithoutDisablingExistingDateActions() throws Exception {


        var original = new SignInUiSession.Cell(20260927, SignInUiParityTest.text("27"), SignInUiParityTest.text(""), null);
        var cells = grid(LocalDate.of(2026, 10, 1), 7, List.of(original));
        var port = new SignInUiParityTest.Port() {
            public List<SignInUiSession.Cell> cells() {
                return cells;
            }
        };
        var view = new RecordingUiView();
        new SignInUiSession(port, () -> true).bind(view);
        assertEquals(true, view.enabled.get("day0"));
        view.actions.get("day0").run();
        assertEquals(List.of(20260927), port.requests);
    }


    @Test
    public void themeStyleOverridesSharedHoverWithoutChangingTheActions() throws Exception {


        var port = new SignInUiParityTest.Port();
        var view = new RecordingUiView();
        new SignInUiSession(port, () -> true).bind(view);
        for (int index = 0; index < 5; index++) {
            assertNotNull(view.icons.get("theme" + index));
            view.actions.get("theme" + index).run();
            assertEquals(index, port.selectedTheme);
        }
        assertEquals(5, port.themes);
    }
}
