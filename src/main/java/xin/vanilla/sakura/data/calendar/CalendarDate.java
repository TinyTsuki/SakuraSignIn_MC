package xin.vanilla.sakura.data.calendar;

import lombok.Value;

/** A date in a registered calendar. Intercalary months share their visible month number. */
@Value
public class CalendarDate {
    int year;
    int month;
    int day;
    boolean intercalary;
}
