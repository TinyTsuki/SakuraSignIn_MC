package xin.vanilla.sakura.data.calendar;

import xin.vanilla.sakura.data.personaldate.LunarCalendar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

/** Immutable runtime calendar snapshot loaded by the server. */
public final class CalendarRegistry {
    private final Map<String, DateCalendar> calendars;

    private CalendarRegistry(Map<String, DateCalendar> calendars) {
        this.calendars = Collections.unmodifiableMap(new LinkedHashMap<>(calendars));
    }

    public static CalendarRegistry builtIns() {
        return fromDefinitions(Collections.singletonList(LunarCalendar.ruleDefinition()));
    }

    public static CalendarRegistry fromDefinitions(Collection<CalendarRuleDefinition> definitions) {
        Map<String, DateCalendar> result = new LinkedHashMap<>();
        register(result, new GregorianDateCalendar());
        if (definitions != null) {
            definitions.forEach(definition -> register(result, new TabularDateCalendar(definition)));
        }
        return new CalendarRegistry(result);
    }

    public Optional<DateCalendar> find(String id) {
        return Optional.ofNullable(calendars.get(id));
    }

    public DateCalendar require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown calendar: " + id));
    }

    public Collection<DateCalendar> values() {
        return calendars.values();
    }

    public List<CalendarDescriptor> descriptors() {
        return calendars.values().stream()
                .map(calendar -> new CalendarDescriptor(
                        calendar.id(), calendar.displayNameKey()))
                .collect(Collectors.toList());
    }

    private static void register(Map<String, DateCalendar> target, DateCalendar calendar) {
        if (target.put(calendar.id(), calendar) != null) {
            throw new IllegalArgumentException("Duplicate calendar: " + calendar.id());
        }
    }
}
