package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 通过真实客户端网络包覆盖签到、补签、奖励同步与重启后的摘要恢复。 */
public final class SakuraNetworkSmokeClientRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(180);
    private static final long STATE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90);
    private static final long SUMMARY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long UI_SAMPLE_SECONDS = Integer.getInteger("sakura.networkSmoke.rewardsPerClaim", 48) > 48 ? 60 : 20;
    private static final long UI_DURATION_NANOS = TimeUnit.SECONDS.toNanos(UI_SAMPLE_SECONDS);
    private static final long UI_CYCLE_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static State state = State.CONNECT;
    private static long startedAt;
    private static long stateStartedAt;
    private static long requestStartedAt;
    private static boolean signInRequested;
    private static boolean reSignInRequested;
    private static boolean inputReady;
    private static boolean sustainedReady;
    private static boolean serverFinished;
    private static Properties checkpoint;
    private static SakuraNetworkSmokeScreens ui;
    private static ReflectiveClientSparkProfile spark;
    private static long uiStartedAt;
    private static long lastUiCycleAt;
    private static int uiCycles;
    private static final SakuraNetworkSmokeDeliveryCheck delivery = new SakuraNetworkSmokeDeliveryCheck();

    private SakuraNetworkSmokeClientRunner() {
    }

    public static void tick() {
        if (!SakuraNetworkSmokeStatus.enabled() || state == State.FINISHED) return;
        Minecraft client = Minecraft.getInstance();
        try {
            long now = System.nanoTime();
            if (startedAt == 0L) {
                startedAt = now;
                stateStartedAt = now;
                String phase = SakuraNetworkSmokeStatus.phase();
                if (!"phase-one".equals(phase) && !"phase-two".equals(phase)) {
                    throw new IllegalStateException("Unknown network smoke phase " + phase);
                }
            }
            long stateTimeout = state == State.FINAL_SUMMARY ? SUMMARY_TIMEOUT_NANOS : STATE_TIMEOUT_NANOS;
            if (now - startedAt > TIMEOUT_NANOS || now - stateStartedAt > stateTimeout) {
                throw new IllegalStateException("Timed out in " + state);
            }
            if (state != State.CONNECT && state != State.LOGIN_SYNC && !remote(client)) {
                throw new IllegalStateException("Remote connection lost in " + state);
            }
            delivery.collect();
            switch (state) {
                case CONNECT:
                    connect(client);
                    break;
                case LOGIN_SYNC:
                    waitForLogin(client);
                    break;
                case SIGN_IN:
                    signIn(client);
                    break;
                case RE_SIGN_IN:
                    reSignIn(client);
                    break;
                case UI_WORKLOAD:
                    runUiWorkload(client);
                    break;
                case SERVER_FINISH:
                    readServerStatus();
                    if (serverFinished) transition(State.FINAL_SUMMARY);
                    break;
                case FINAL_SUMMARY:
                    waitForFinalSummary(client);
                    break;
                default:
                    break;
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private static void connect(Minecraft client) {
        if (client.getOverlay() != null) return;
        String host = System.getProperty("sakura.networkSmoke.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("sakura.networkSmoke.port", "25578"));
        ServerData server = new ServerData("Sakura Network Smoke", host + ':' + port, false);
        ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString(server.ip), server);
        transition(State.LOGIN_SYNC);
    }

    private static boolean remote(Minecraft client) {
        return client.player != null && client.level != null && client.getConnection() != null
                && client.getConnection().getConnection().isConnected()
                && !client.getConnection().getConnection().isMemoryConnection()
                && client.getSingleplayerServer() == null;
    }

    private static void waitForLogin(Minecraft client) throws java.io.IOException {
        if (!remote(client) || !SakuraClientState.isEnabled()) return;
        // enabled is set by the real summary packet handler; the status file alone is not a sync acknowledgement.
        readServerStatus();
        if ("phase-two".equals(SakuraNetworkSmokeStatus.phase())) {
            SakuraNetworkSmokeStatus.append("PASS remote-login-sync");
            transition(State.SERVER_FINISH);
            return;
        }
        if (!inputReady) return;
        if (serverFinished) throw new IllegalStateException("Server finished before initial client requests");
        // The server resets the input fixture after login. Wait for that summary, not a stale login value.
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        if (data.getTotalSignInDays() != 0 || data.getSignInCard() != 1) return;
        SakuraNetworkSmokeStatus.append("PASS remote-login-sync");
        transition(State.SIGN_IN);
    }

    private static void signIn(Minecraft client) {
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        if (data.getTotalSignInDays() == 0 && !signInRequested) {
            requestStartedAt = System.nanoTime();
            SakuraNetwork.sendToServer(new SignInPacket(DateUtils.toDateTimeString(new Date()),
                    true, ESignInType.SIGN_IN));
            signInRequested = true;
            return;
        }
        if (data.getTotalSignInDays() == 0) return;
        if (!signInRequested || data.getTotalSignInDays() != 1) {
            throw new IllegalStateException("Unexpected sign-in count " + data.getTotalSignInDays());
        }
        long roundtripNanos = System.nanoTime() - requestStartedAt;
        SakuraNetworkSmokeStatus.append("PASS sign-in-client");
        SakuraNetworkSmokeStatus.append("PASS sign-in-client-network-roundtrip wall-ns=" + roundtripNanos);
        transition(State.RE_SIGN_IN);
    }

    private static void reSignIn(Minecraft client) {
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        if (data.getTotalSignInDays() == 1 && !reSignInRequested) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            requestStartedAt = System.nanoTime();
            SakuraNetwork.sendToServer(new SignInPacket(DateUtils.toDateTimeString(calendar.getTime()),
                    true, ESignInType.RE_SIGN_IN));
            reSignInRequested = true;
            return;
        }
        if (data.getTotalSignInDays() == 1) return;
        if (!reSignInRequested || data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
            throw new IllegalStateException("Unexpected re-sign-in summary: days="
                    + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
        }
        long roundtripNanos = System.nanoTime() - requestStartedAt;
        SakuraNetworkSmokeStatus.append("PASS re-sign-in-client");
        SakuraNetworkSmokeStatus.append("PASS re-sign-in-client-network-roundtrip wall-ns=" + roundtripNanos);
        transition(State.UI_WORKLOAD);
    }

    private static void runUiWorkload(Minecraft client) throws java.io.IOException {
        readServerStatus();
        if (!sustainedReady) return;
        if (client.getOverlay() != null) return;
        if (ui == null) {
            ui = new SakuraNetworkSmokeScreens(() -> spark != null && !spark.future.isDone());
            ui.open(client);
            spark = ReflectiveClientSparkProfile.start();
            uiStartedAt = System.nanoTime();
            SakuraNetworkSmokeStatus.append("PASS client-ui-spark-profiler-active");
        }
        long now = System.nanoTime();
        if (spark.future.isDone() && !ui.verified()) {
            throw new IllegalStateException("Client Spark sampling ended before 20 rendered content cycles: " + ui.summary());
        }
        if (!spark.future.isDone() && (lastUiCycleAt == 0L || now - lastUiCycleAt >= UI_CYCLE_NANOS)) {
            // Never count a content change until the previous view has had a real render call.
            if (ui.readyForCycle(client)) {
                ui.runCycle(client, ++uiCycles);
                lastUiCycleAt = System.nanoTime();
            }
        }
        if (spark.writeWhenComplete()) {
            SakuraNetworkSmokeStatus.append("PASS client-ui-spark-report-written");
        }
        if (spark.future.isDone() && !ui.verified()) {
            throw new IllegalStateException("Client Spark sampling ended during incomplete UI coverage: " + ui.summary());
        }
        if (spark.written() && now - uiStartedAt >= UI_DURATION_NANOS
                && uiCycles >= 20 && ui.verified()) {
            SakuraNetworkSmokeStatus.append("PASS client-ui-sustained-workload cycles=" + uiCycles
                    + " duration-wall-ns=" + (now - uiStartedAt) + " " + ui.summary());
            ui.close(client);
            transition(State.SERVER_FINISH);
        }
    }

    private static void readServerStatus() throws java.io.IOException {
        Path status = configuredPath("sakura.networkSmoke.serverStatus");
        if (!Files.isRegularFile(status)) return;
        List<String> lines = Files.readAllLines(status, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.startsWith("FAIL")) throw new IllegalStateException("Server reported " + line);
        }
        inputReady |= lines.contains("PASS input-ready");
        sustainedReady |= lines.contains("PASS sustained-ready");
        serverFinished |= lines.contains("FINISHED " + SakuraNetworkSmokeStatus.phase());
    }

    private static void waitForFinalSummary(Minecraft client) throws Exception {
        readServerStatus();
        if (!serverFinished) return;
        if (checkpoint == null) {
            Path path = configuredPath("sakura.networkSmoke.checkpoint");
            // The checkpoint must be completely written before the exact server FINISHED marker.
            Properties loaded = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded.load(reader);
            }
            UUID player = UUID.fromString(requiredProperty(loaded, "player"));
            if (!player.equals(client.player.getUUID())) {
                throw new IllegalStateException("Checkpoint player mismatch: " + player);
            }
            if (checkpointInt(loaded, "cycles") < 20) {
                throw new IllegalStateException("Checkpoint has fewer than 20 workload cycles");
            }
            checkpointInt(loaded, "totalDays");
            checkpointInt(loaded, "cards");
            checkpoint = loaded;
        }
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        int days = checkpointInt(checkpoint, "totalDays");
        int cards = checkpointInt(checkpoint, "cards");
        // Network tasks can reach the render thread after the server writes FINISHED. Allow a bounded sync drain.
        if (!SakuraClientState.isEnabled() || data.getTotalSignInDays() != days || data.getSignInCard() != cards) {
            return;
        }
        if (!delivery.verify(client, checkpoint)) return;
        String summary = " player=" + client.player.getUUID() + " days=" + days + " cards=" + cards
                + " cycles=" + checkpointInt(checkpoint, "cycles");
        SakuraNetworkSmokeStatus.append("PASS final-summary-client" + summary);
        if ("phase-two".equals(SakuraNetworkSmokeStatus.phase())) {
            SakuraNetworkSmokeStatus.append("PASS persisted-player-data-client" + summary);
        }
        finish(client);
    }

    private static Path configuredPath(String property) {
        String configured = System.getProperty(property, "").trim();
        if (configured.isEmpty()) throw new IllegalStateException("Missing " + property);
        return Paths.get(configured).toAbsolutePath();
    }

    private static String requiredProperty(Properties values, String key) {
        String value = values.getProperty(key, "").trim();
        if (value.isEmpty()) throw new IllegalStateException("Missing checkpoint property " + key);
        return value;
    }

    private static int checkpointInt(Properties values, String key) {
        int value = Integer.parseInt(requiredProperty(values, key));
        if (value < 0) throw new IllegalStateException("Negative checkpoint property " + key);
        return value;
    }

    private static void transition(State next) {
        state = next;
        stateStartedAt = System.nanoTime();
    }

    private static void finish(Minecraft client) {
        state = State.FINISHED;
        if ("phase-one".equals(SakuraNetworkSmokeStatus.phase())) {
            xin.vanilla.sakura.config.ClientConfig.get().display().specialVariant(false);
            xin.vanilla.sakura.config.ClientConfig.save();
        }
        xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeConfigs.verify(true);
        SakuraNetworkSmokeStatus.append("FINISHED " + SakuraNetworkSmokeStatus.phase());
        LOGGER.info("Sakura network smoke client finished {}", SakuraNetworkSmokeStatus.phase());
        client.stop();
    }

    private static void fail(Minecraft client, String message) {
        state = State.FINISHED;
        if (ui != null) {
            try {
                ui.close(client);
            } catch (Throwable cleanupError) {
                LOGGER.warn("Unable to restore client smoke UI state", cleanupError);
            }
        }
        SakuraNetworkSmokeStatus.append("FAIL client " + message);
        LOGGER.error("Sakura network smoke client failed: {}", message);
        client.stop();
    }

    private enum State {
        CONNECT, LOGIN_SYNC, SIGN_IN, RE_SIGN_IN, UI_WORKLOAD, SERVER_FINISH, FINAL_SUMMARY, FINISHED
    }

    /** Owns a dev-only Fabric client plugin and exports its native protobuf. */
    private static final class ReflectiveClientSparkProfile {
        private final Object sampler;
        private final Future<?> future;
        private final Object platform;
        private final Object plugin;
        private final Path report;
        private boolean written;

        private ReflectiveClientSparkProfile(Object sampler, Future<?> future, Object platform, Object plugin, Path report) {
            this.sampler = sampler;
            this.future = future;
            this.platform = platform;
            this.plugin = plugin;
            this.report = report;
        }

        private static ReflectiveClientSparkProfile start() {
            try {
                Path report = configuredPath("sakura.networkSmoke.clientSparkReport");
                Object plugin = plugin();
                ClassLoader loader = plugin.getClass().getClassLoader();
                Field platformField = base(plugin).getDeclaredField("platform");
                platformField.setAccessible(true);
                Object platform = platformField.get(plugin);
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 4.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, UI_SAMPLE_SECONDS, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                Thread renderThread = Thread.currentThread();
                if (!"Render thread".equals(renderThread.getName())) {
                    throw new IllegalStateException("Client smoke is not running on Render thread");
                }
                // Keep the sampler on the render thread even before Spark's game-thread dumper is ready.
                Class<?> specific = Class.forName("me.lucko.spark.common.sampler.ThreadDumper$Specific", true, loader);
                Object dumper = specific.getConstructor(Thread.class).newInstance(renderThread);
                builderType.getMethod("threadDumper", dumperType).invoke(builder, dumper);
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", grouperType).invoke(builder, grouperType.getField("BY_POOL").get(null));
                Object container = platform.getClass().getMethod("getSamplerContainer").invoke(platform);
                // Replace the owned plugin's background sampler only inside this isolated smoke session.
                method(container.getClass(), "stopActiveSampler", 1).invoke(container, false);
                // SamplerBuilder.start already starts sampling. Calling sampler.start again doubles the samples.
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                method(container.getClass(), "setActiveSampler", 1).invoke(container, sampler);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveClientSparkProfile(sampler, future, platform, plugin, report);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start client Spark sampler for network smoke", error);
            }
        }

        private boolean written() {
            return written;
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                future.get();
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
                Object props = propsType.getConstructor().newInstance();
                Class<?> senderData = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data", true, loader);
                Object creator = senderData.getConstructor(String.class, UUID.class).newInstance("Sakura client UI smoke", null);
                propsType.getMethod("creator", senderData).invoke(props, creator);
                propsType.getMethod("comment", String.class).invoke(props, "Sakura client UI smoke");
                propsType.getMethod("mergeMode", Supplier.class).invoke(props, (Supplier<Object>) () -> mergeMode(loader));
                Object lookup = platform.getClass().getMethod("createClassSourceLookup").invoke(platform);
                propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, (Supplier<Object>) () -> lookup);
                Object proto = method(sampler.getClass(), "toProto", 2).invoke(sampler, platform, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Client Spark report was empty");
                Files.createDirectories(report.getParent());
                Files.write(report, bytes);
                plugin.getClass().getMethod("disable").invoke(plugin);
                written = true;
                return true;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted writing client Spark report", error);
            } catch (ReflectiveOperationException | java.io.IOException | java.util.concurrent.ExecutionException error) {
                throw new IllegalStateException("Unable to write client Spark report", error);
            }
        }

        private static Object mergeMode(ClassLoader loader) {
            try {
                Class<?> disambiguator = Class.forName("me.lucko.spark.common.util.MethodDisambiguator", true, loader);
                Class<?> merge = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                return merge.getMethod("sameMethod", disambiguator).invoke(null, disambiguator.getConstructor().newInstance());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark merge mode", error);
            }
        }

        private static Object plugin() throws ReflectiveOperationException {
            Class<?> modType = Class.forName("me.lucko.spark.fabric.FabricSparkMod");
            Field mod = modType.getDeclaredField("mod");
            mod.setAccessible(true);
            Object modInstance = mod.get(null);
            if (modInstance == null) throw new IllegalStateException("Fabric Spark mod was not initialized");
            Class<?> pluginType = Class.forName("me.lucko.spark.fabric.plugin.FabricClientSparkPlugin");
            Object plugin = pluginType.getConstructor(modType, Minecraft.class)
                    .newInstance(modInstance, Minecraft.getInstance());
            pluginType.getMethod("enable").invoke(plugin);
            return plugin;
        }

        private static Class<?> base(Object plugin) {
            Class<?> type = plugin.getClass();
            while (type != null && !type.getName().equals("me.lucko.spark.fabric.plugin.FabricSparkPlugin")) type = type.getSuperclass();
            if (type == null) throw new IllegalStateException("Spark base plugin was not found");
            return type;
        }

        private static Method method(Class<?> type, String name, int parameters) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) return candidate;
            }
            throw new IllegalStateException("Missing Spark method " + type.getName() + '#' + name);
        }
    }
}
