package xin.vanilla.sakura.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.sakura.SakuraComponent;

import java.time.LocalDateTime;

/**
 * 解析指令中的绝对或相对日期时间片段。
 */
public final class CommandDateTimeParser {
    public enum Kind implements IEnumDescribable {
        DATE(new String[]{"year", "month", "day"}),
        TIME(new String[]{"hour", "minute", "second"}),
        DATE_TIME(new String[]{"year", "month", "day", "hour", "minute", "second"});

        private final String[] units;

        Kind(String[] units) {
            this.units = units;
        }

        @Override
        public Component enumDescription() {
            return SakuraComponent.get().literal(name());
        }
    }

    private CommandDateTimeParser() {
    }

    public static long parse(String input, Kind kind, LocalDateTime base) throws CommandSyntaxException {
        if (input == null || input.trim().isEmpty()) {
            throw invalidInt(input);
        }
        String[] parts = input.trim().split("\\s+");
        if (parts.length != kind.units.length) {
            throw invalidInt(input);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int offset = 0;
            if (part.startsWith("_") || part.startsWith("~")) {
                offset = baseValue(base, kind.units[i]);
                part = part.length() == 1 ? "0" : part.substring(1);
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException exception) {
                throw invalidInt(part);
            }
            result.append("year".equals(kind.units[i])
                    ? String.format("%04d", offset + value)
                    : String.format("%02d", offset + value));
        }
        try {
            return Long.parseLong(result.toString());
        } catch (NumberFormatException exception) {
            throw invalidInt(input);
        }
    }

    public static void validateClockValue(double time) throws CommandSyntaxException {
        if (time < -23.59 || time > 23.59) {
            throw parseError(time);
        }
        String[] parts = String.format("%05.2f", time).split("\\.");
        if (parts.length != 2) {
            throw parseError(time);
        }
        int hour;
        int minute;
        try {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw parseError(time);
        }
        if (hour < -23 || hour > 23 || minute < 0 || minute > 59) {
            throw parseError(time);
        }
    }

    private static int baseValue(LocalDateTime base, String unit) {
        switch (unit) {
            case "year":
                return base.getYear();
            case "month":
                return base.getMonthValue();
            case "day":
                return base.getDayOfMonth();
            case "hour":
                return base.getHour();
            case "minute":
                return base.getMinute();
            case "second":
                return base.getSecond();
            default:
                return 0;
        }
    }

    private static CommandSyntaxException invalidInt(Object value) {
        return CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(String.valueOf(value));
    }

    private static CommandSyntaxException parseError(Object value) {
        return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(value);
    }
}
