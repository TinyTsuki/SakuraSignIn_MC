package xin.vanilla.sakura.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

/**
 * 日期参数解析不依赖加载器事件，便于所有版本复用同一行为。
 */
public class CommandDateTimeParserTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 7, 29, 18, 35, 42);

    @Test
    public void resolvesAbsoluteAndRelativeDateTimeParts() throws Exception {
        assertEquals(20260801183541L,
                CommandDateTimeParser.parse("2026 8 1 ~ ~ ~-1", CommandDateTimeParser.Kind.DATE_TIME, BASE));
        assertEquals(20260728L,
                CommandDateTimeParser.parse("~ ~ ~-1", CommandDateTimeParser.Kind.DATE, BASE));
    }

    @Test(expected = CommandSyntaxException.class)
    public void rejectsWrongPartCount() throws Exception {
        CommandDateTimeParser.parse("2026 7", CommandDateTimeParser.Kind.DATE, BASE);
    }

    @Test(expected = CommandSyntaxException.class)
    public void rejectsInvalidClockMinute() throws Exception {
        CommandDateTimeParser.validateClockValue(12.75);
    }
}
