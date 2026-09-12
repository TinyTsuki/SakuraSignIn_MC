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
    private static boolean runtimeRecorded;

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
            if (!runtimeRecorded) {
                recordRuntime(path);
                runtimeRecorded = true;
            }
            Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to write Sakura network smoke status", error);
        }
    }

    private static void recordRuntime(Path status) throws IOException {
        Class<?>[] boundaries = {xin.vanilla.banira.common.data.Component.class,
                xin.vanilla.banira.common.util.MessageUtils.class,
                xin.vanilla.banira.common.notification.NotificationBudget.class};
        java.util.Map<Path, String> hashes = new java.util.HashMap<>();
        try {
            for (Class<?> boundary : boundaries) {
                Path jar = Paths.get(boundary.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!Files.isRegularFile(jar)) throw new IOException("Expected dependency jar: " + jar);
                String hash = hashes.get(jar);
                if (hash == null) {
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                    try (java.io.InputStream input = Files.newInputStream(jar)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    }
                    StringBuilder hex = new StringBuilder(64);
                    for (byte part : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
                    hash = hex.toString();
                    hashes.put(jar, hash);
                }
                String evidence = "RUNTIME " + boundary.getName() + " sha256=" + hash
                        + " size=" + Files.size(jar) + " path=" + jar + System.lineSeparator();
                Files.write(status, evidence.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (java.net.URISyntaxException | java.security.NoSuchAlgorithmException error) {
            throw new IOException("Unable to fingerprint loaded Banira", error);
        }
    }
}
