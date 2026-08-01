package xin.vanilla.sakura.data.calendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.sakura.SakuraSignIn;

import java.io.IOException;

/** Owns the authoritative calendar snapshot for the current server session. */
public final class SakuraCalendars {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile CalendarRegistry registry = CalendarRegistry.builtIns();

    private SakuraCalendars() {
    }

    public static void reload() {
        CalendarRuleRepository repository = new CalendarRuleRepository(
                BaniraDataPaths.gameConfigPath().resolve(SakuraSignIn.MODID));
        try {
            registry = repository.loadOrCreate();
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to load calendar rules; built-in rules will be used", failure);
            registry = CalendarRegistry.builtIns();
        }
    }

    public static CalendarRegistry get() {
        return registry;
    }
}
