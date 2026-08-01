package xin.vanilla.sakura.data.calendar;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CalendarRuleDocument {
    private int schemaVersion = 1;
    private List<CalendarRuleDefinition> calendars = new ArrayList<>();
}
