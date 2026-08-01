package xin.vanilla.sakura.data.calendar;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthRule {
    private int month;
    private int days;
    private boolean intercalary;
}
