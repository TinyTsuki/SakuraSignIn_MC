package xin.vanilla.sakura.client.ui;

import xin.vanilla.sakura.SakuraComponent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class SignInCalendarGrid {
    private SignInCalendarGrid() { }

    public static List<SignInUiSession.Cell> complete(LocalDate month, int weekStart, List<SignInUiSession.Cell> existing) {
        LocalDate first = month.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStart)));
        Map<Integer, SignInUiSession.Cell> byDate = new HashMap<>();
        for (SignInUiSession.Cell cell : existing) byDate.put(cell.date(), cell);
        List<SignInUiSession.Cell> result = new ArrayList<>(42);
        for (int index = 0; index < 42; index++) {
            LocalDate date = first.plusDays(index);
            int key = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
            boolean current = date.getYear() == month.getYear() && date.getMonthValue() == month.getMonthValue();
            SignInUiSession.Cell cell = byDate.get(key);
            result.add(cell == null ? new SignInUiSession.Cell(key,
                    SakuraComponent.get().literal(Integer.toString(date.getDayOfMonth())), SakuraComponent.get().literal(""), null, current, false)
                    : new SignInUiSession.Cell(key, cell.day(), cell.tooltip(), cell.icon(), current, cell.actionable()));
        }
        return List.copyOf(result);
    }
}
