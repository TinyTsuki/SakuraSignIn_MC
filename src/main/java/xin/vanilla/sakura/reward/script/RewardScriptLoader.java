package xin.vanilla.sakura.reward.script;

import xin.vanilla.banira.api.script.*;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.script.*;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Startup-only loading, bounded input and atomic registration. No network source loading.
 */
public final class RewardScriptLoader {
    private static final String PACKAGE = "xin.vanilla.banira.generated.";
    private static volatile List<ScriptRewardDescriptor> descriptors = Collections.emptyList();

    private RewardScriptLoader() {
    }

    public static List<ScriptSource> readSources(Path directory, Path... legacyDirectories) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        collectSources(directory, files);
        for (Path legacy : legacyDirectories) collectSources(legacy, files);
        List<ScriptSource> sources = new ArrayList<>();
        long total = 0;
        for (Path path : files.values()) {
            String file = path.getFileName().toString();
            String name = file.substring(0, file.length() - 5);
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > 262144) throw new IOException("Invalid reward script: " + file);
            byte[] bytes;
            try (java.io.InputStream input = Files.newInputStream(path);
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 262144) throw new IOException("Reward script too large: " + file);
                    output.write(buffer, 0, length);
                }
                bytes = output.toByteArray();
            }
            total += bytes.length;
            if (total > 1048576) throw new IOException("Reward scripts exceed 1 MiB");
            String source = new String(bytes, StandardCharsets.UTF_8);
            if (source.startsWith("\uFEFF")) source = source.substring(1);
            sources.add(new ScriptSource(name, PACKAGE + name, file, source));
        }
        return sources;
    }

    private static void collectSources(Path directory, Map<String, Path> files) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid reward script directory: " + directory);
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.java")) {
            for (Path path : paths) {
                String file = path.getFileName().toString();
                if (!file.endsWith(".java")) continue;
                files.putIfAbsent(file, path);
                if (files.size() > 32) throw new IOException("At most 32 reward scripts are allowed");
            }
        }
    }

    public static void load(Path directory, Path... legacyDirectories) {
        List<ScriptSource> sources;
        try {
            sources = readSources(directory, legacyDirectories);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read reward scripts from " + directory, error);
        }
        if (sources.isEmpty()) {
            clear();
            return;
        }
        // Pump only this session's constructor callbacks on the server owner thread.
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        try (ScriptSession<RewardScript> session = BaniraScripts.open("sakura-rewards", RewardScript.class,
                "1", new ScriptLimits(262144, 1048576, 32), callbacks::add)) {
            CompletableFuture<PreparedScripts<RewardScript>> future = session.prepare(sources);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!future.isDone()) {
                if (System.nanoTime() >= deadline) throw new TimeoutException("Reward script compilation timed out");
                Runnable callback = callbacks.poll(50, TimeUnit.MILLISECONDS);
                if (callback != null) callback.run();
            }
            PreparedScripts<RewardScript> prepared = future.get();
            List<RewardTypeDefinition<?>> definitions = new ArrayList<>();
            List<ScriptRewardDescriptor> descriptions = new ArrayList<>();
            for (RewardScript script : prepared.scripts().values()) {
                RewardTypeDefinition<JsonObject> definition = Objects.requireNonNull(script.definition());
                ScriptRewardDescriptor descriptor = Objects.requireNonNull(script.descriptor());
                if (!definition.getId().equals(descriptor.id()) || !(definition.getCodec() instanceof JsonRewardCodec)) {
                    throw new IllegalArgumentException("Script rewards must use matching IDs and JsonRewardCodec");
                }
                List<RewardViolation> errors = definition.getValidator().validate(descriptor.defaults());
                if (errors != null && !errors.isEmpty())
                    throw new IllegalArgumentException("Invalid default reward: " + descriptor.id());
                definitions.add(definition);
                descriptions.add(descriptor);
            }
            SakuraRewards.replaceScriptTypes(definitions);
            descriptors = Collections.unmodifiableList(descriptions);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reward script loading interrupted", error);
        } catch (Exception | LinkageError error) {
            throw new IllegalStateException("Unable to load reward scripts from " + directory + "; rewards were not loaded", error);
        }
    }

    public static List<ScriptRewardDescriptor> descriptors() {
        return descriptors;
    }

    public static List<ScriptRewardDescriptor> descriptors(String language) {
        List<ScriptRewardDescriptor> result = new ArrayList<>();
        for (ScriptRewardDescriptor descriptor : descriptors) {
            xin.vanilla.banira.common.data.Component description = xin.vanilla.sakura.reward.RewardOperations.describe(language,
                    new xin.vanilla.sakura.reward.Reward(descriptor.defaults(), descriptor.id()), false);
            String name = description == null ? null : description.getString(language, true, true);
            result.add(name == null || name.trim().isEmpty() || name.length() > 128 || name.equals(descriptor.id().toString())
                    ? descriptor : descriptor.withName(name));
        }
        return Collections.unmodifiableList(result);
    }

    public static void clear() {
        SakuraRewards.replaceScriptTypes(Collections.emptyList());
        descriptors = Collections.emptyList();
    }
}
