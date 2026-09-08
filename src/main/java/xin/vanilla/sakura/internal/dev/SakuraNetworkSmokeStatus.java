package xin.vanilla.sakura.internal.dev;

import xin.vanilla.banira.api.BaniraEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** 仅供开发期跨进程烟测交换进度，不参与正常游戏逻辑。 */
public final class SakuraNetworkSmokeStatus {
    private static final String ENABLED_PROPERTY = "sakura.networkSmoke";
    private static final String PHASE_PROPERTY = "sakura.networkSmoke.phase";
    private static final String STATUS_PROPERTY = "sakura.networkSmoke.status";

    private SakuraNetworkSmokeStatus() {
    }

    public static boolean enabled() {
        return !BaniraEnvironment.isProduction()
                && Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    }

    public static String phase() {
        return System.getProperty(PHASE_PROPERTY, "");
    }

    public static synchronized void append(String line) {
        if (!enabled()) return;
        String value = System.getProperty(STATUS_PROPERTY, "");
        if (value.trim().isEmpty()) throw new IllegalStateException("Missing " + STATUS_PROPERTY);
        try {
            Path path = Paths.get(value);
            Files.createDirectories(path.getParent());
            Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to write Sakura network smoke status", error);
        }
    }
}
