package xin.vanilla.sakura.internal.server.dev;

import xin.vanilla.banira.common.util.DateUtils;

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
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeConfigs;
import xin.vanilla.sakura.reward.Reward;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
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
    private static SakuraNetworkSmokeWorkload sustainedWorkload;
    private static SakuraNetworkSmokeRewardWorkload rewardWorkload;
    private static int workloadTicks;
    private static long workloadStartedAt;
    private static String originalTime;
    private static String originalCalibration;
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
                SakuraNetworkSmokeConfigs.recordRuntime();
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
            LOGGER.error("Sakura network smoke failed", error);
        }
    }

    private static void initialize(ServerPlayerEntity player) {
        initialized = true;
        if ("phase-one".equals(SakuraNetworkSmokeStatus.phase())) {
            CommonConfig.get().server().autoSignIn(false).requiredTotalOnlineSeconds(0).requiredTodayOnlineSeconds(0);
            CommonConfig.get().makeUp().signInCard(true).signInCardOnlyBaseReward(true);
            CommonConfig.get().cooling().timeCoolingMethod(ETimeCoolingMethod.FIXED_TIME).timeCoolingTime(0);
            CommonConfig.get().history().retentionMonths(0);
            CommonConfig.get().reward().rewardAffectedByLuck(false);
            originalTime = CommonConfig.get().dateTime().serverTime();
            originalCalibration = CommonConfig.get().dateTime().serverCalibrationTime();
            CommonConfig.save();
            RewardConfig rewardConfig = new RewardConfig();
            rewardConfig.getBaseRewards().add(new Reward(new ItemStack(Items.APPLE, 5), SakuraRewardTypes.ITEM));
            rewardConfig.getBaseRewards().add(new Reward("give @s minecraft:gold_nugget 1",
                    SakuraRewardTypes.COMMAND));
            for (int index = 0; index < 18; index++) {
                rewardConfig.getBaseRewards().add(new Reward(new ItemStack(Items.CARROT),
                        SakuraRewardTypes.ITEM));
            }
            RewardConfigManager.setRewardConfig(rewardConfig);
            IPlayerSignInData data = SakuraPlayerData.get(player);
            data.setSignInCard(1);
            SakuraPlayerData.saveAndSync(player);
            SakuraNetworkSmokeStatus.append("PASS input-ready");
        }
    }

    private static void runWritePhase(ServerPlayerEntity player) throws java.io.IOException {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (!signInVerified && data.getTotalSignInDays() == 1) {
            assertAppleReward(player, 5);
            signInVerified = true;
            SakuraNetworkSmokeStatus.append("PASS sign-in-reward");
        }
        if (!signInVerified || data.getTotalSignInDays() < 2) return;
        if (!reSignInVerified) {
            String clientStatus = System.getProperty("sakura.networkSmoke.clientStatus", "");
            if (clientStatus.isEmpty()) throw new IllegalStateException("Missing client status path");
            Path path = Paths.get(clientStatus);
            if (!Files.isRegularFile(path) || !Files.readAllLines(path, StandardCharsets.UTF_8)
                    .contains("PASS re-sign-in-client")) return;
            if (data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
                throw new IllegalStateException("Re-sign-in persistence state invalid: days="
                        + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
            }
            assertAppleReward(player, 10);
            assertItemReward(player, Items.GOLD_NUGGET, 2);
            SakuraNetworkSmokeStatus.append("PASS command-reward");
            assertItemReward(player, Items.CARROT, 36);
            SakuraNetworkSmokeStatus.append("PASS expanded-reward-list");
            reSignInVerified = true;
            SakuraNetworkSmokeStatus.append("PASS re-sign-in-reward");

            long preparedAt = System.nanoTime();
            java.util.Date now = SakuraClock.serverNow();
            SakuraNetworkSmokeWorkload.seedHistoricalRecords(data, player.getUUID(), now);
            sustainedWorkload = new SakuraNetworkSmokeWorkload(data, now);
            rewardWorkload = new SakuraNetworkSmokeRewardWorkload(now);
            rewardWorkload.prepare(player);
            SakuraNetworkSmokeStatus.append("PASS historical-sign-in-fixture records=" + data.getSignInRecords().size()
                    + " months=" + data.getMonthIndexes().size() + " preparation-ns=" + (System.nanoTime() - preparedAt));
            sparkProfile = ReflectiveSparkProfile.start(serverOf(player));
            workloadStartedAt = System.nanoTime();
            SakuraNetworkSmokeStatus.append("PASS spark-profiler-active");
            SakuraNetworkSmokeStatus.append("PASS sustained-ready");
            return;
        }
        if (System.nanoTime() - workloadStartedAt > TimeUnit.SECONDS.toNanos(90)) {
            throw new IllegalStateException("Sustained reward workload timed out");
        }
        boolean operationsPending = !rewardWorkload.complete() || sustainedWorkload.ticks() < 320;
        if (operationsPending && sparkProfile.future.isDone()) {
            throw new IllegalStateException("Sampler expired before sustained operations");
        }
        if (++workloadTicks % 10 == 0 && !rewardWorkload.complete()) rewardWorkload.runCycle(player);
        boolean historyComplete = sustainedWorkload.tick();
        if (operationsPending && sparkProfile.future.isDone()) {
            throw new IllegalStateException("Sampler expired during sustained operations");
        }
        if (!historyComplete || !rewardWorkload.complete() || !sparkProfile.written) return;
        rewardWorkload.report();
        SakuraNetworkSmokeStatus.append("PASS sustained-history-reward-workload " + sustainedWorkload.timingSummary());
        CommonConfig.get().dateTime().serverTime(originalTime).serverCalibrationTime(originalCalibration);
        CommonConfig.save();
        SakuraPlayerData.saveAndSync(player);
        SakuraNetworkSmokeRewardWorkload.assertAllRecordedRewards(data, rewardWorkload.finalSignedDay());
        Properties checkpoint = new Properties();
        checkpoint.setProperty("player", player.getUUID().toString());
        checkpoint.setProperty("totalDays", Integer.toString(data.getTotalSignInDays()));
        checkpoint.setProperty("cards", Integer.toString(data.getSignInCard()));
        checkpoint.setProperty("cycles", Integer.toString(rewardWorkload.cycles()));
        checkpoint.setProperty("rewardsPerClaim", Integer.toString(SakuraNetworkSmokeRewardWorkload.REWARDS_PER_CLAIM));
        checkpoint.setProperty("records", Integer.toString(data.getSignInRecords().size()));
        checkpoint.setProperty("months", Integer.toString(data.getMonthIndexes().size()));
        checkpoint.setProperty("lastSignedDay", Long.toString(rewardWorkload.finalSignedDay().getTime()));
        checkpoint.setProperty("lastMakeUpDay", Long.toString(rewardWorkload.finalMakeUpDay().getTime()));
        String language = xin.vanilla.sakura.util.SakuraUtils.getPlayerLanguage(player);
        checkpoint.setProperty("notificationLanguage", language);
        for (int cycle = 0; cycle < SakuraNetworkSmokeRewardWorkload.CYCLES; cycle++) {
            java.util.Date day = DateUtils.addDay(rewardWorkload.finalSignedDay(),
                    -3 * (SakuraNetworkSmokeRewardWorkload.CYCLES - cycle - 1));
            for (int offset = 0; offset <= 1; offset++) {
                java.util.Date date = DateUtils.addDay(day, -offset);
                int key = cycle * 2 + offset;
                java.util.List<Reward> expected = SakuraNetworkSmokeRewardWorkload.createRewardConfig(date).getBaseRewards();
                for (int index = 0; index < expected.size(); index++) {
                    checkpoint.setProperty("notificationDetail." + key + "." + index,
                            xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeNotificationSnapshot.json(
                                    expected.get(index).getName(language, true).color(java.awt.Color.GREEN.getRGB()), language));
                }
                checkpoint.setProperty("pressureDay." + key, Long.toString(date.getTime()));
                xin.vanilla.sakura.data.SignInRecord record = data.getSignInRecords().stream()
                        .filter(value -> DateUtils.toDateInt(value.getCompensateTime()) == DateUtils.toDateInt(date))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Missing pressure record"));
                checkpoint.setProperty("pressureRecord." + key, record.writeToNBT().toString());
            }
        }
        try (java.io.Writer writer = Files.newBufferedWriter(checkpointPath(), StandardCharsets.UTF_8)) {
            checkpoint.store(writer, "Sakura sustained reward restart checkpoint");
        }
        SakuraNetworkSmokeStatus.append("PASS final-checkpoint cycles=" + rewardWorkload.cycles()
                + " days=" + data.getTotalSignInDays() + " records=" + data.getSignInRecords().size());
        SakuraNetworkSmokeConfigs.verifyLocal(CommonConfig.class);
        SakuraNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    private static Path checkpointPath() {
        String value = System.getProperty("sakura.networkSmoke.checkpoint", "").trim();
        if (value.isEmpty()) throw new IllegalStateException("Missing checkpoint path");
        return Paths.get(value);
    }

    private static void runVerifyPhase(ServerPlayerEntity player) throws java.io.IOException {
        Properties checkpoint = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(checkpointPath(), StandardCharsets.UTF_8)) {
            checkpoint.load(reader);
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (!player.getUUID().toString().equals(checkpoint.getProperty("player"))
                || Integer.parseInt(checkpoint.getProperty("cycles")) != SakuraNetworkSmokeRewardWorkload.CYCLES
                || Integer.parseInt(checkpoint.getProperty("rewardsPerClaim")) != SakuraNetworkSmokeRewardWorkload.REWARDS_PER_CLAIM
                || Integer.parseInt(checkpoint.getProperty("totalDays")) != 42
                || data.getTotalSignInDays() != Integer.parseInt(checkpoint.getProperty("totalDays"))
                || data.getSignInCard() != 0 || Integer.parseInt(checkpoint.getProperty("cards")) != 0) {
            throw new IllegalStateException("Persisted player data was not restored: days="
                    + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
        }
        if (data.getSignInRecords().size() != Integer.parseInt(checkpoint.getProperty("records"))
                || data.getSignInRecords().size() != SakuraNetworkSmokeWorkload.HISTORY_RECORD_COUNT + 42
                || data.getMonthIndexes().size() != Integer.parseInt(checkpoint.getProperty("months"))
                || data.getMonthIndexes().size() < SakuraNetworkSmokeWorkload.HISTORY_MONTHS
                || !data.isRewardedOn(new java.util.Date(Long.parseLong(checkpoint.getProperty("lastSignedDay"))))
                || !data.isRewardedOn(new java.util.Date(Long.parseLong(checkpoint.getProperty("lastMakeUpDay"))))) {
            throw new IllegalStateException("Historical sign-in data was not restored: records="
                    + data.getSignInRecords().size() + ", months=" + data.getMonthIndexes().size());
        }
        SakuraNetworkSmokeRewardWorkload.assertInventory(player, 10, 3,
                2 * (SakuraNetworkSmokeRewardWorkload.REWARDS_PER_CLAIM - 2));
        SakuraNetworkSmokeRewardWorkload.assertAllRecordedRewards(data,
                new java.util.Date(Long.parseLong(checkpoint.getProperty("lastSignedDay"))));
        SakuraNetworkSmokeStatus.append("PASS persisted-all-pressure-days days="
                + (SakuraNetworkSmokeRewardWorkload.CYCLES * 2) + " rewards-per-day="
                + SakuraNetworkSmokeRewardWorkload.REWARDS_PER_CLAIM);
        SakuraNetworkSmokeStatus.append("PASS persisted-final-cycle cycles=" + checkpoint.getProperty("cycles")
                + " days=" + data.getTotalSignInDays() + " records=" + data.getSignInRecords().size());
        SakuraNetworkSmokeStatus.append("PASS persisted-player-data");
        SakuraNetworkSmokeConfigs.verifyLocal(CommonConfig.class);
        SakuraNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }

    private static MinecraftServer serverOf(ServerPlayerEntity player) {
        return player.getServer();
    }

    private static void assertAppleReward(ServerPlayerEntity player, int minimum) {
        assertItemReward(player, Items.APPLE, minimum);
    }

    private static void assertItemReward(ServerPlayerEntity player, net.minecraft.item.Item item, int minimum) {
        int count = 0;
        for (ItemStack stack : player.inventory.items) {
            if (stack.getItem() == item) count += stack.getCount();
        }
        if (count < minimum) {
            throw new IllegalStateException("Reward item was not delivered, expected at least "
                    + minimum + ", got " + count + " for " + item.getRegistryName());
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
                // Sample only the game thread at 10ms; all-thread sampling distorts this legacy runtime.
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 10.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class)
                        .invoke(builder, 30L, TimeUnit.SECONDS);
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
