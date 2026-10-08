package xin.vanilla.sakura.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.time.LocalDate;

public final class DeletionDateParser {
    private DeletionDateParser() {
    }

    public static LocalDate parse(String input, LocalDate base) throws CommandSyntaxException {
        try {
            String value = input.trim();
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                LocalDate day = LocalDate.parse(value);
                if (day.getYear() < 1) throw new IllegalArgumentException("Invalid year");
                return day;
            }
            String[] parts = value.split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Expected year month day");
            int year = part(parts[0], base.getYear());
            int month = part(parts[1], base.getMonthValue());
            int day = part(parts[2], base.getDayOfMonth());
            if (year < 1 || year > 9999) throw new IllegalArgumentException("Invalid year");
            return LocalDate.of(year, month, day);
        } catch (RuntimeException invalid) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(input);
        }
    }

    private static int part(String value, int base) {
        if (value.startsWith("~") || value.startsWith("_")) {
            return Math.addExact(base, value.length() == 1 ? 0 : Integer.parseInt(value.substring(1)));
        }
        return Integer.parseInt(value);
    }
}
