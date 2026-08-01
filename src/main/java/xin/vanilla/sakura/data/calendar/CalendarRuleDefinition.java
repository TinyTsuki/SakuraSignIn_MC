package xin.vanilla.sakura.data.calendar;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** A finite sequential calendar table anchored to an ISO date. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarRuleDefinition {
    private String id;
    private String displayNameKey;
    private String epochIsoDate;
    private int firstYear;
    private List<CalendarYearRule> years = new ArrayList<>();
}
