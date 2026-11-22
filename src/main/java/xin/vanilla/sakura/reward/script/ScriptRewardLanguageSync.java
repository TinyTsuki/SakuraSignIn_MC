package xin.vanilla.sakura.reward.script;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-thread, connection-scoped metadata synchronization.
 */
public final class ScriptRewardLanguageSync {
    private final Map<Object, String> languages = new IdentityHashMap<>();

    public void sync(Object connection, String language, Consumer<String> send) {
        if (connection == null) return;
        String normalized = language == null || language.trim().isEmpty() ? "en_us"
                : language.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        if (normalized.equals(languages.get(connection))) return;
        send.accept(normalized);
        languages.put(connection, normalized);
    }

    public void remove(Object connection) {
        languages.remove(connection);
    }

    public void clear() {
        languages.clear();
    }
}
