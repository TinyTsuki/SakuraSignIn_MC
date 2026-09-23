package xin.vanilla.sakura.internal.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

/** Read-only config comparison; only the separate dev checkpoint is written. */
public final class SakuraNetworkSmokeConfigs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SakuraNetworkSmokeConfigs() { }

    public static void verify(boolean client) {
        if (!SakuraNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke disabled");
        Class<?> configClass = client ? ClientConfig.class : CommonConfig.class;
        ConfigHolder holder = BaniraConfig.holder(configClass);
        if (holder == null) throw new IllegalStateException("Missing config holder " + configClass.getName());
        String phase = SakuraNetworkSmokeStatus.phase();
        try {
            java.net.URI source = xin.vanilla.banira.api.BaniraConfigs.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path runtime = java.nio.file.Paths.get(source);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(runtime));
            StringBuilder hash = new StringBuilder();
            for (byte value : digest) hash.append(String.format("%02x", value & 255));
            SakuraNetworkSmokeStatus.append("RUNTIME banira sha256=" + hash + " path=" + runtime.toAbsolutePath());
            verifyGenerated(client ? ClientConfig.get() : CommonConfig.get(), "", holder);
            JsonObject snapshot = verify(holder, BaniraDataPaths.gameConfigPath(), phase);
            SakuraNetworkSmokeStatus.append(("phase-one".equals(phase)
                    ? "PASS complete-config-snapshot" : "PASS complete-config-restart")
                    + " fields=" + snapshot.size() + " file=" + holder.getConfigName() + ".toml");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot verify config " + holder.getConfigName(), error);
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
        Map<String, Object> disk = readRawToml(toml, holder);
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            String path = entry.getKey();
            if (!disk.containsKey(path)) throw new IllegalStateException("Missing TOML value " + path);
            Object value = disk.get(path);
            requireEqual("TOML", path, entry.getValue(), normalize(value));
        }
        Path checkpoint = directory.resolve(name + ".smoke-checkpoint.json");
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

    private static Map<String, Object> readRawToml(Path file, ConfigHolder holder) throws IOException {
        // Reuse only the backend's pure parsers; its constructor/load would fill missing fields with defaults.
        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        try {
            Method valueStore = ConfigHolder.class.getDeclaredMethod("valueStore");
            valueStore.setAccessible(true);
            Object backend = valueStore.invoke(holder);
            Method strip = backend.getClass().getDeclaredMethod("stripTomlComment", String.class);
            Method find = backend.getClass().getDeclaredMethod("findUnquoted", String.class, char.class);
            Method parse = backend.getClass().getDeclaredMethod("parseToml", ConfigEntryDescriptor.class, String.class);
            strip.setAccessible(true);
            find.setAccessible(true);
            parse.setAccessible(true);
            Map<String, Object> values = new HashMap<>();
            String table = "";
            for (String original : lines) {
                String line = ((String) strip.invoke(backend, original)).trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    table = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                int equals = (Integer) find.invoke(backend, line, '=');
                if (equals <= 0) throw new IllegalStateException("Invalid TOML line " + line);
                String key = line.substring(0, equals).trim();
                String path = table.isEmpty() ? key : table + "." + key;
                ConfigEntryDescriptor descriptor = holder.getDescriptor(path);
                if (descriptor == null) continue;
                if (values.containsKey(path)) throw new IllegalStateException("Duplicate TOML value " + path);
                Object value = parse.invoke(backend, descriptor, line.substring(equals + 1).trim());
                if (value == null || !holder.validate(path, value)) {
                    throw new IllegalStateException("Invalid TOML value " + path);
                }
                values.put(path, value);
            }
            return values;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect Fabric TOML parser", error);
        }
    }

    private static void verifyGenerated(Object view, String prefix, ConfigHolder holder) {
        try {
            for (java.lang.reflect.Method method : view.getClass().getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())
                        || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0 || method.getName().equals("handle")) continue;
                Object value = method.invoke(view);
                String path = prefix + method.getName();
                if (method.getReturnType().getEnclosingClass() == view.getClass()) {
                    verifyGenerated(value, path + ".", holder);
                } else {
                    requireEqual("generated", path, normalize(holder.get(path)), normalize(value));
                }
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
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
}
