package xin.vanilla.sakura.internal.server.dev;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.api.event.BaniraEvents;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.reward.Reward;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 在真实专服 tick 与客户端网络包之间验证签到、补签、奖励和持久化。 */
public final class SakuraNetworkSmokeServerRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean ready;
    private static boolean initialized;
    private static boolean signInVerified;
    private static boolean reSignInVerified;
    private static boolean finished;
    private static int shutdownTicks;
    private static ReflectiveSparkProfile sparkProfile;

    private SakuraNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (SakuraNetworkSmokeStatus.enabled()) {
            BaniraEvents.Server.onTick(event -> onServerTick());
        }
    }

    private static void onServerTick() {
        try {
            MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
            if (server == null || !server.isRunning()) return;
            if (!ready) {
                ready = true;
                SakuraNetworkSmokeStatus.append("PASS server-ready");
            }
            if (sparkProfile != null && sparkProfile.writeWhenComplete()) {
                SakuraNetworkSmokeStatus.append("PASS spark-report-written");
            }
            if (finished) {
                shutdownWhenSaved(server);
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayerEntity player = server.getPlayerList().getPlayers().get(0);
            if (!initialized) initialize(player);
            if ("phase-one".equals(SakuraNetworkSmokeStatus.phase())) {
                runWritePhase(player);
            } else if ("phase-two".equals(SakuraNetworkSmokeStatus.phase())) {
                runVerifyPhase(player);
            } else {
                throw new IllegalStateException("Unknown network smoke phase " + SakuraNetworkSmokeStatus.phase());
            }
        } catch (Throwable error) {
            finished = true;
            SakuraNetworkSmokeStatus.append("FAIL server " + error);
            throw error;
        }
    }

    private static void initialize(ServerPlayerEntity player) {
        initialized = true;
        if ("phase-one".equals(SakuraNetworkSmokeStatus.phase())) {
            CommonConfig.get().server().autoSignIn(false);
            CommonConfig.get().makeUp().signInCard(true).signInCardOnlyBaseReward(true);
            CommonConfig.save();
            RewardConfig rewardConfig = new RewardConfig();
            rewardConfig.getBaseRewards().add(new Reward(new ItemStack(Items.APPLE, 5), SakuraRewardTypes.ITEM));
            RewardConfigManager.setRewardConfig(rewardConfig);
            IPlayerSignInData data = SakuraPlayerData.get(player);
            data.setSignInCard(1);
            SakuraPlayerData.saveAndSync(player);
            sparkProfile = ReflectiveSparkProfile.start(serverOf(player));
            SakuraNetworkSmokeStatus.append("PASS spark-profiler-active");
        }
    }

    private static void runWritePhase(ServerPlayerEntity player) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (!signInVerified && data.getTotalSignInDays() == 1) {
            assertAppleReward(player, 5);
            signInVerified = true;
            SakuraNetworkSmokeStatus.append("PASS sign-in-reward");
        }
        if (!signInVerified || reSignInVerified || data.getTotalSignInDays() < 2) return;
        if (data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
            throw new IllegalStateException("Re-sign-in persistence state invalid: days="
                    + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
        }
        assertAppleReward(player, 10);
        SakuraPlayerData.saveAndSync(player);
        reSignInVerified = true;
        SakuraNetworkSmokeStatus.append("PASS re-sign-in-reward");
        SakuraNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    private static void runVerifyPhase(ServerPlayerEntity player) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
            throw new IllegalStateException("Persisted player data was not restored: days="
                    + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
        }
        SakuraNetworkSmokeStatus.append("PASS persisted-player-data");
        SakuraNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }

    private static MinecraftServer serverOf(ServerPlayerEntity player) {
        return player.getServer();
    }

    private static void assertAppleReward(ServerPlayerEntity player, int minimum) {
        int count = 0;
        for (ItemStack stack : player.inventory.items) {
            if (stack.getItem() == Items.APPLE) count += stack.getCount();
        }
        if (count < minimum) {
            throw new IllegalStateException("Reward items not delivered, expected at least " + minimum + ", got " + count);
        }
    }

    private static void shutdownWhenSaved(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() > 0) {
            shutdownTicks = 0;
            return;
        }
        if (++shutdownTicks >= 40) {
            SakuraNetworkSmokeStatus.append("PASS server-shutdown");
            server.halt(false);
        }
    }

    /** 旧 Spark 没有稳定的导出 facade；只用反射调用它自己的 sampler 与 protobuf 报告。 */
    private static final class ReflectiveSparkProfile {
        private static final String REPORT_PROPERTY = "sakura.networkSmoke.sparkReport";
        private final Object sampler;
        private final Future<?> future;
        private final Object platform;
        private final Object plugin;
        private final MinecraftServer server;
        private final Path reportPath;
        private boolean written;

        private ReflectiveSparkProfile(Object sampler, Future<?> future, Object platform,
                                       Object plugin, MinecraftServer server, Path reportPath) {
            this.sampler = sampler;
            this.future = future;
            this.platform = platform;
            this.plugin = plugin;
            this.server = server;
            this.reportPath = reportPath;
        }

        private static ReflectiveSparkProfile start(MinecraftServer server) {
            try {
                String configuredPath = System.getProperty(REPORT_PROPERTY, "").trim();
                if (configuredPath.isEmpty()) throw new IllegalStateException("Missing " + REPORT_PROPERTY);
                Object plugin = findServerPlugin();
                Object platform = platform(plugin);
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                // 旧版 Spark 的全线程采样在 1.16.5 开发运行时使用 4ms 频率会触发 watchdog。
                // 20ms 仍能覆盖实际签到窗口，同时避免基线工具干扰被测服务端。
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 10.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class)
                        .invoke(builder, 20L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                builderType.getMethod("threadDumper", dumperType).invoke(builder, gameThreadDumper(plugin));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", grouperType).invoke(builder,
                        grouperType.getField("BY_POOL").get(null));
                Object sampler = findMethod(builderType, "start", 1).invoke(builder, platform);
                Future<?> future = (Future<?>) findMethod(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveSparkProfile(sampler, future, platform, plugin, server,
                        Paths.get(configuredPath).toAbsolutePath());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start Spark sampler", error);
            }
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> commandSource = Class.forName("net.minecraft.command.ICommandSource", true, loader);
                Class<?> pluginType = Class.forName("me.lucko.spark.forge.plugin.ForgeSparkPlugin", true, loader);
                Class<?> senderType = Class.forName("me.lucko.spark.forge.ForgeCommandSender", true, loader);
                Object sender = senderType.getConstructor(commandSource, pluginType)
                        .newInstance(server, plugin);
                Class<?> orderType = Class.forName("me.lucko.spark.common.sampler.ThreadNodeOrder", true, loader);
                Object order = orderType.getField("BY_TIME").get(null);
                Class<?> disambiguatorType = Class.forName("me.lucko.spark.common.util.MethodDisambiguator", true, loader);
                Object disambiguator = disambiguatorType.getConstructor().newInstance();
                Class<?> mergeModeType = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                Object mergeMode = mergeModeType.getMethod("sameMethod", disambiguatorType)
                        .invoke(null, disambiguator);
                Object lookup = pluginType.getMethod("createClassSourceLookup").invoke(plugin);
                Object proto = findMethod(sampler.getClass(), "toProto", 6).invoke(sampler, platform,
                        sender, order, "Sakura network smoke", mergeMode, lookup);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark report was empty");
                Files.createDirectories(reportPath.getParent());
                Files.write(reportPath, bytes);
                written = true;
                return true;
            } catch (ReflectiveOperationException | java.io.IOException error) {
                throw new IllegalStateException("Unable to write Spark report", error);
            }
        }

        private static Object platform(Object plugin) throws ReflectiveOperationException {
            Field field = pluginType(plugin).getDeclaredField("platform");
            field.setAccessible(true);
            return field.get(plugin);
        }

        private static Object gameThreadDumper(Object plugin) throws ReflectiveOperationException {
            Class<?> pluginType = pluginType(plugin);
            Field field = pluginType.getDeclaredField("threadDumper");
            field.setAccessible(true);
            Object gameThread = field.get(plugin);
            gameThread.getClass().getMethod("ensureSetup").invoke(gameThread);
            return pluginType.getMethod("getDefaultThreadDumper").invoke(plugin);
        }

        private static Class<?> pluginType(Object plugin) {
            Class<?> type = plugin.getClass();
            while (type != null && !type.getName().equals("me.lucko.spark.forge.plugin.ForgeSparkPlugin")) {
                type = type.getSuperclass();
            }
            if (type == null) throw new IllegalStateException("ForgeSparkPlugin base type was not found");
            return type;
        }

        private static Object findServerPlugin() throws ReflectiveOperationException {
            Field listenersField = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Object listenersValue = listenersField.get(MinecraftForge.EVENT_BUS);
            if (!(listenersValue instanceof Map)) {
                throw new IllegalStateException("Forge event bus listeners were not a map");
            }
            for (Object candidate : ((Map<?, ?>) listenersValue).keySet()) {
                if (candidate != null && candidate.getClass().getName()
                        .equals("me.lucko.spark.forge.plugin.ForgeServerSparkPlugin")) {
                    return candidate;
                }
            }
            throw new IllegalStateException("ForgeServerSparkPlugin was not registered on the event bus");
        }

        private static Method findMethod(Class<?> type, String name, int parameterCount) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
            }
            throw new IllegalStateException("Missing Spark method " + type.getName() + '#' + name);
        }
    }
}
