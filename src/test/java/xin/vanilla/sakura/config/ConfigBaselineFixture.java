package xin.vanilla.sakura.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.internal.neoforge.config.NeoForgeConfigAdapter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/** Captures actual scanner and access behavior before replacing the handwritten views. */
public final class ConfigBaselineFixture implements ConfigValueStore {
    static { xin.vanilla.sakura.test.NeoForgeUnitTestBootstrap.bootstrap(); }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    final Map<String, Object> values = new TreeMap<>();
    final Map<String, Object> defaults = new TreeMap<>();
    final Set<String> written = new TreeSet<>();
    final List<ConfigEntryDescriptor> descriptors = new ArrayList<>();
    final ConfigHolder holder;
    final ModConfigSpec spec;
    int saves;

    ConfigBaselineFixture(Class<?> configClass) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Map<String, ModConfigSpec.ConfigValue<?>> fields = new LinkedHashMap<>();
        Map<String, String> tooltips = new LinkedHashMap<>();
        Map<String, xin.vanilla.banira.common.config.ConfigCategoryTitleSpec> titles = new LinkedHashMap<>();
        // Use the production registration scanner without registering a fake FML mod container.
        Method scan = NeoForgeConfigAdapter.class.getDeclaredMethod("buildFromClass",
                ModConfigSpec.Builder.class, Class.class, String.class,
                List.class, Map.class, Map.class, Map.class);
        scan.setAccessible(true);
        scan.invoke(null, builder, configClass, "", descriptors, fields, tooltips, titles);
        spec = builder.build();
        for (ConfigEntryDescriptor descriptor : descriptors) {
            defaults.put(descriptor.getPath(), copy(descriptor.getDefaultValue()));
            values.put(descriptor.getPath(), copy(descriptor.getDefaultValue()));
        }
        assertEquals("Every scanned value needs a descriptor", fields.keySet(), values.keySet());
        Config annotation = configClass.getAnnotation(Config.class);
        holder = ConfigHolder.create("baseline", annotation.name(), annotation.type(),
                this, descriptors, tooltips, titles);
    }

    Map<String, Object> schema() {
        Map<String, Object> result = new TreeMap<>();
        for (ConfigEntryDescriptor descriptor : descriptors) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", descriptor.getValueType().name());
            field.put("default", descriptor.getDefaultValue());
            field.put("min", descriptor.getMinValue());
            field.put("max", descriptor.getMaxValue());
            field.put("precision", descriptor.getDecimalPlaces());
            field.put("enum", descriptor.getEnumClass() == null ? null : descriptor.getEnumClass().getName());
            field.put("permission", descriptor.getEditPermissionPolicy().name());
            field.put("permissionLevel", descriptor.getFieldEditPermissionLevel());
            field.put("permissionKey", descriptor.getFieldEditVirtualPermissionKey());
            field.put("keyChords", descriptor.isKeyChords());
            result.put(descriptor.getPath(), field);
        }
        return result;
    }

    void bind(Class<?> type) { bind(type, holder); }

    static org.junit.rules.ExternalResource platformScope() {
        return new org.junit.rules.ExternalResource() {
            private xin.vanilla.banira.platform.BaniraPlatform previous;
            @Override protected void before() {
                xin.vanilla.sakura.test.BaniraTestPlatform.install();
                previous = xin.vanilla.banira.platform.BaniraPlatforms.get();
            }
            @Override protected void after() {
                xin.vanilla.banira.platform.BaniraPlatforms.install(previous);
            }
        };
    }

    public static ConfigHolder holderWithValues(Class<?> type, Map<String, Object> overrides) throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(type);
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            fixture.holder.set(entry.getKey(), entry.getValue());
        }
        return fixture.holder;
    }

    static void bind(Class<?> type, xin.vanilla.banira.platform.BaniraConfigHandle holder) {
        xin.vanilla.banira.platform.BaniraConfigService service = new xin.vanilla.banira.platform.BaniraConfigService() {
            public <T> void register(Class<T> config, String modId) { throw new UnsupportedOperationException(); }
            public <T> T view(Class<?> config, Class<T> view) { throw new UnsupportedOperationException(); }
            public xin.vanilla.banira.platform.BaniraConfigHandle handle(Class<?> config) {
                return config == type ? holder : null;
            }
        };
        xin.vanilla.banira.platform.BaniraPlatforms.install((xin.vanilla.banira.platform.BaniraPlatform)
                Proxy.newProxyInstance(ConfigBaselineFixture.class.getClassLoader(),
                        new Class<?>[]{xin.vanilla.banira.platform.BaniraPlatform.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("configService")) return service;
                            throw new UnsupportedOperationException(method.toString());
                        }));
    }

    static Map<String, Object> readView(Object view, Class<?> type) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        collect(view, type, "", result);
        return result;
    }

    private static void collect(Object view, Class<?> type, String prefix,
                                Map<String, Object> output) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE
                    || method.getName().equals("holder") || method.getName().equals("handle")) continue;
            Object value = method.invoke(view);
            String path = prefix + method.getName();
            if ((method.getReturnType().isInterface() || method.getReturnType().getEnclosingClass() == type)
                    && !List.class.isAssignableFrom(method.getReturnType())) {
                collect(value, method.getReturnType(), path + ".", output);
            } else {
                output.put(path, copy(value));
            }
        }
    }

    void nonDefaultValues() {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) value = !((Boolean) value);
            else if (value instanceof Integer) value = ((Integer) value) == 7 ? 8 : 7;
            else if (value instanceof Long) value = ((Long) value) == 7L ? 8L : 7L;
            else if (value instanceof Double) value = ((Double) value) == 0.125D ? 0.25D : 0.125D;
            else if (value instanceof String) value = "baseline," + entry.getKey();
            else if (value instanceof Enum) {
                Object[] constants = ((Enum<?>) value).getDeclaringClass().getEnumConstants();
                value = constants[(((Enum<?>) value).ordinal() + 1) % constants.length];
            } else if (value instanceof List) {
                value = ((List<?>) value).isEmpty() ? sampleList(entry.getKey())
                        : new ArrayList<>();
            } else throw new AssertionError("Unhandled baseline type: " + entry.getKey());
            values.put(entry.getKey(), value);
        }
    }

    private List<?> sampleList(String path) {
        ConfigEntryDescriptor descriptor = holder.getDescriptor(path);
        switch (descriptor.getValueType()) {
            case STRING_LIST: return new ArrayList<>(Arrays.asList("tick, clazz -> tick >= 5", "minecraft:arrow"));
            case INTEGER_LIST: return new ArrayList<>(Collections.singletonList(7));
            case LONG_LIST: return new ArrayList<>(Collections.singletonList(7L));
            case DOUBLE_LIST: return new ArrayList<>(Collections.singletonList(0.125D));
            case BOOLEAN_LIST: return new ArrayList<>(Collections.singletonList(true));
            case ENUM_LIST: return new ArrayList<>(Collections.singletonList(descriptor.getEnumClass().getEnumConstants()[0]));
            default: throw new AssertionError(path);
        }
    }

    static void assertSnapshot(String name, Object actual) throws Exception {
        JsonElement actualJson = JSON.toJsonTree(actual);
        Path output = Paths.get("build", "config-view-baseline", name + ".json");
        Files.createDirectories(output.getParent());
        Files.write(output, JSON.toJson(actualJson).getBytes(StandardCharsets.UTF_8));
        try (InputStream input = ConfigBaselineFixture.class.getResourceAsStream(
                "/config-view-baseline/" + name + ".json")) {
            assertNotNull("Review the initial capture before accepting baseline: " + output, input);
            JsonElement expected = JSON.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonElement.class);
            assertEquals(name, expected, actualJson);
        }
    }

    static Object copy(Object value) {
        return value instanceof List ? new ArrayList<>((List<?>) value) : value;
    }

    @Override public Set<String> paths() { return values.keySet(); }
    @Override public Object get(String path) { return values.get(path); }
    @Override public void set(String path, Object value) {
        if (!values.containsKey(path)) throw new AssertionError("Unknown write: " + path);
        values.put(path, copy(value));
        written.add(path);
    }
    @Override public Class<?> valueClass(String path) {
        Object value = defaults.get(path);
        if (value instanceof Enum) return ((Enum<?>) value).getDeclaringClass();
        return value instanceof List ? List.class : value.getClass();
    }
    @Override public Object defaultValue(String path) { return copy(defaults.get(path)); }
    @Override public boolean validate(String path, Object value) {
        ModConfigSpec.ValueSpec definition = spec.getSpec().get(path);
        return definition != null && definition.test(value);
    }
    @Override public void save() { saves++; }
}
