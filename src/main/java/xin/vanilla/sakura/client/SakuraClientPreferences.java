package xin.vanilla.sakura.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.sakura.SakuraSignIn;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 保存不适合进入同步配置的纯客户端界面偏好。 */
public final class SakuraClientPreferences {
    private static final Path FILE = BaniraDataPaths.gameConfigPath()
            .resolve(SakuraSignIn.MODID).resolve("client-state.json");
    private static boolean loaded;
    private static String lastLotteryPoolId = "";

    private SakuraClientPreferences() {
    }

    public static synchronized String lastLotteryPoolId() {
        load();
        return lastLotteryPoolId;
    }

    public static synchronized void lastLotteryPoolId(String value) {
        load();
        String normalized = value == null ? "" : value;
        if (normalized.equals(lastLotteryPoolId)) return;
        lastLotteryPoolId = normalized;
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.isRegularFile(FILE)) {
                JsonObject root = new JsonParser().parse(new String(
                        Files.readAllBytes(FILE), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("lastLotteryPoolId")) {
                    lastLotteryPoolId = root.get("lastLotteryPoolId").getAsString();
                }
            }
        } catch (Exception ignored) {
            lastLotteryPoolId = "";
        }
    }

    private static void save() {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("lastLotteryPoolId", lastLotteryPoolId);
            Files.write(temporary, root.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // 界面偏好写入失败不应影响抽奖流程。
        }
    }
}
