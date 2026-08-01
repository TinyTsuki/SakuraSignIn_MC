package xin.vanilla.sakura.data.calendar;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarYearRule {
    private int year;
    private List<CalendarMonthRule> months = new ArrayList<>();
}
