package xin.vanilla.sakura.internal.dev;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraftforge.fml.ModList;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.ClientConfigView;
import xin.vanilla.sakura.config.CommonConfigView;
import xin.vanilla.banira.api.Banira;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.notification.NotificationBudget;
import xin.vanilla.banira.common.util.MessageUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Read-only config comparison; only the separate dev checkpoint is written. */
public final class SakuraNetworkSmokeConfigs {
    private static final class RetainedViews {
        static final CommonConfigView COMMON = CommonConfigView.get();
        static final ClientConfigView CLIENT = ClientConfigView.get();
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> RUNTIME_PHASES = new HashSet<>();

    private SakuraNetworkSmokeConfigs() { }

    public static void verifyLocal(Class<?> configClass) {
        if (!SakuraNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke disabled");
        ConfigHolder holder = BaniraConfigs.holder(configClass);
        if (holder == null) throw new IllegalStateException("Missing config holder " + configClass.getName());
        String phase = SakuraNetworkSmokeStatus.phase();
        verifyGeneratedReads(holder, configClass == ClientConfig.class ? RetainedViews.CLIENT : RetainedViews.COMMON, "");
        SakuraNetworkSmokeStatus.append("PASS generated-config-reads config=" + holder.getConfigName()
                + " phase=" + phase + " values=" + holder.getDescriptors().size());
        try {
            JsonObject snapshot = verify(holder, BaniraDataPaths.gameConfigPath(), phase);
            SakuraNetworkSmokeStatus.append(("phase-one".equals(phase)
                    ? "PASS complete-config-snapshot" : "PASS complete-config-restart")
                    + " config=" + holder.getConfigName() + " values=" + snapshot.size());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify config " + holder.getConfigName(), error);
        }
    }

    private static void verifyGeneratedReads(ConfigHolder holder, Object view, String prefix) {
        for (java.lang.reflect.Method method : view.getClass().getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0 || method.getName().equals("handle")) continue;
            try {
                Object value = method.invoke(view);
                String path = prefix + method.getName();
                if (method.getReturnType().getEnclosingClass() == view.getClass()) {
                    verifyGeneratedReads(holder, value, path + ".");
                } else {
                    if (!holder.hasValue(path)) throw new IllegalStateException("Unknown generated path: " + path);
                    requireEqual("generated view", path, normalize(holder.get(path)), normalize(value));
                }
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot inspect generated config", error);
            }
        }
    }

    static JsonObject verify(ConfigHolder holder, Path directory, String phase) throws IOException {
        if (!"phase-one".equals(phase) && !"phase-two".equals(phase)) {
            throw new IllegalStateException("Unknown config smoke phase " + phase);
        }
        JsonObject current = new JsonObject();
        for (String path : new TreeSet<>(holder.valuePaths())) {
            Object value = holder.get(path);
            if (value == null) throw new IllegalStateException("Null holder value " + path);
            current.add(path, normalize(value));
        }
        if (current.size() == 0) throw new IllegalStateException("Config holder is empty");
        String name = holder.getConfigName();
        Path toml = directory.resolve(name.endsWith(".toml") ? name : name + ".toml");
        // Parse independently, never load/save the live backend or split serialized list text.
        CommentedConfig disk;
        try (Reader reader = Files.newBufferedReader(toml, StandardCharsets.UTF_8)) {
            disk = new TomlParser().parse(reader);
        }
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            String path = entry.getKey();
            if (!disk.contains(path)) throw new IllegalStateException("Missing TOML value " + path);
            Object value = disk.get(path);
            requireEqual("TOML", path, entry.getValue(), normalize(value));
        }
        Path checkpoint = directory.resolve(name + "-network-smoke-config.json");
        if ("phase-one".equals(phase)) {
            try (Writer writer = Files.newBufferedWriter(checkpoint, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
        } else {
            JsonObject saved;
            try (Reader reader = Files.newBufferedReader(checkpoint, StandardCharsets.UTF_8)) {
                saved = GSON.fromJson(reader, JsonObject.class);
            }
            if (saved == null || !paths(current).equals(paths(saved))) {
                throw new IllegalStateException("Checkpoint config paths differ from holder: " + name);
            }
            for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
                requireEqual("checkpoint", entry.getKey(), saved.get(entry.getKey()), entry.getValue());
            }
        }
        return current;
    }

    private static Set<String> paths(JsonObject object) {
        Set<String> paths = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) paths.add(entry.getKey());
        return paths;
    }

    private static JsonElement normalize(Object value) {
        if (value instanceof Enum<?>) return new JsonPrimitive(((Enum<?>) value).name());
        if (value instanceof Iterable<?>) {
            JsonArray array = new JsonArray();
            for (Object element : (Iterable<?>) value) array.add(normalize(element));
            return array;
        }
        return GSON.toJsonTree(value);
    }

    private static void requireEqual(String source, String path, JsonElement expected, JsonElement actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Config " + source + " mismatch " + path
                    + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static synchronized void recordRuntime() {
        if (!SakuraNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke disabled");
        String phase = SakuraNetworkSmokeStatus.phase();
        if (RUNTIME_PHASES.contains(phase)) return;
        try {
            Object mod = ModList.get().getModContainerById(Banira.MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("Banira mod container unavailable")).getMod();
            String baniraSource = BaniraCodex.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            if (mod == null || !baniraSource.equals(mod.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm())) {
                throw new IllegalStateException("Banira mod container does not own the loaded facade");
            }
            Path path = ModList.get().getModFileById(Banira.MOD_ID).getFile().getFilePath().toAbsolutePath();
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Banira mod file unavailable: " + path);
            byte[] bytes = Files.readAllBytes(path);
            StringBuilder hash = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
            for (Class<?> type : new Class<?>[]{Component.class, MessageUtils.class, NotificationBudget.class}) {
                // Forge modjar URLs are not file URIs; bind each class to the registered entrypoint.
                String source = type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if (!baniraSource.equals(source) || type.getClassLoader() != BaniraCodex.class.getClassLoader()) {
                    throw new IllegalStateException("Loaded class does not match Banira mod source/loader: " + type.getName() + " " + source);
                }
                SakuraNetworkSmokeStatus.append("RUNTIME " + type.getName() + " codeSource=" + source
                        + " loader-match=banira-entrypoint sha256=" + hash + " size=" + bytes.length + " path=" + path);
            }
            RUNTIME_PHASES.add(phase);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot fingerprint loaded Banira", error);
        }
    }
}
