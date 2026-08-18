package xin.vanilla.sakura.internal.forge.event;

import xin.vanilla.sakura.data.time.SakuraClock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import xin.vanilla.banira.common.util.BaniraScheduler;
import xin.vanilla.banira.common.util.ReflectionUtils;
import xin.vanilla.banira.common.util.StringUtils;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.reward.personaldate.PersonalDateDeliveryResult;
import xin.vanilla.sakura.reward.personaldate.PersonalDateOnlineCheckSchedule;
import xin.vanilla.sakura.reward.personaldate.PersonalDateRewardDispatcher;
import xin.vanilla.sakura.data.time.SakuraOnlineTime;
import xin.vanilla.sakura.data.time.OnlineTimeRequirementResult;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Forge 原生事件转换为 Sakura 业务调用。
 */
public final class ForgeSakuraGameEventAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int MAX_CLIENT_SETTINGS_ATTEMPTS = 120;
    private static final PersonalDateOnlineCheckSchedule ONLINE_REWARD_CHECK =
            new PersonalDateOnlineCheckSchedule(20L * 60L * 5L);
    private static String languageFieldName;

    private ForgeSakuraGameEventAdapter() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(ForgeSakuraGameEventAdapter::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ForgeSakuraGameEventAdapter::onPlayerCloned);
        MinecraftForge.EVENT_BUS.addListener(ForgeSakuraGameEventAdapter::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeSakuraGameEventAdapter::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(ForgeSakuraGameEventAdapter::onServerTick);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        SignInCommand.register(event.getDispatcher());
    }

    /**
     * 数据按 UUID 缓存，重生时只复制语言并同步新玩家实体。
     */
    private static void onPlayerCloned(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer)
                || !(event.getPlayer() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer original = (ServerPlayer) event.getOriginal();
        ServerPlayer replacement = (ServerPlayer) event.getPlayer();
        copyPlayerLanguage(original, replacement);
        SakuraPlayerData.sync(replacement);
    }

    private static void copyPlayerLanguage(ServerPlayer original,
                                           ServerPlayer replacement) {
        if (StringUtils.isNullOrEmpty(languageFieldName)) {
            for (String field : ReflectionUtils.getPrivateFieldNames(
                    ServerPlayer.class, String.class)) {
                Object value = ReflectionUtils.getPrivateFieldValue(
                        ServerPlayer.class, original, field);
                if (original.getLanguage().equals(value)) {
                    languageFieldName = field;
                    break;
                }
            }
        }
        if (StringUtils.isNotNullOrEmpty(languageFieldName)) {
            ReflectionUtils.setPrivateFieldValue(ServerPlayer.class,
                    replacement, languageFieldName, original.getLanguage());
        }
    }

    /**
     * 旧数据迁移失败时不得继续自动签到或删除旧数据。
     */
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        try {
            LegacyMigrationResult result = SakuraPlayerData.migrateAndLoad(player);
            if (result != LegacyMigrationResult.NO_LEGACY_DATA) {
                LOGGER.info("Legacy player data migration for {}: {}", player.getUUID(), result);
            }
        } catch (Exception migrationFailure) {
            LOGGER.error("Legacy player data migration failed for {}; automatic sign-in is skipped",
                    player.getUUID(), migrationFailure);
            return;
        }
        normalizeOnlineTime(player, RewardManager.getCompensateDate(SakuraClock.serverNow()));
        deliverOnlineRewards(player);
        waitForClientSettings(player, 0);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            SakuraPlayerData.removeServer(event.getPlayer().getUUID());
        }
    }

    private static void waitForClientSettings(ServerPlayer player, int attempt) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        BaniraScheduler.scheduleAfterMillis(server, 500, () -> {
            if (!isConnected(server, player)) {
                return;
            }
            if (player.getChatVisibility() == null && attempt < MAX_CLIENT_SETTINGS_ATTEMPTS) {
                waitForClientSettings(player, attempt + 1);
                return;
            }
            BaniraScheduler.scheduleAfterMillis(server, 500,
                    () -> runAutomaticSignIn(server, player));
        });
    }

    private static void runAutomaticSignIn(MinecraftServer server, ServerPlayer player) {
        if (!isConnected(server, player)) {
            return;
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (!CommonConfig.get().server().autoSignIn()
                || RewardManager.isSignedIn(data, SakuraClock.serverNow(), true)) {
            return;
        }
        java.util.Date now = RewardManager.getCompensateDate(SakuraClock.serverNow());
        java.time.LocalDate day = now.toInstant().atZone(
                java.time.ZoneId.systemDefault()).toLocalDate();
        OnlineTimeRequirementResult onlineTime = SakuraOnlineTime.evaluate(player, data, day);
        if (!onlineTime.isAllowed()) {
            long missingSeconds = Math.max(onlineTime.getMissingTotalSeconds(),
                    onlineTime.getMissingTodaySeconds());
            long retrySeconds = Math.max(1L, Math.min(3600L, missingSeconds));
            BaniraScheduler.scheduleAfterMillis(server, retrySeconds * 1000L,
                    () -> runAutomaticSignIn(server, player));
            return;
        }
        RewardManager.signIn(player, new SignInPacket(
                DateUtils.toDateTimeString(SakuraClock.serverNow()),
                data.isAutoRewarded(), ESignInType.SIGN_IN));
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        java.util.Date now = RewardManager.getCompensateDate(SakuraClock.serverNow());
        java.time.LocalDate day = now.toInstant().atZone(
                java.time.ZoneId.systemDefault()).toLocalDate();
        if (!ONLINE_REWARD_CHECK.shouldCheck(server.getTickCount(), day)) {
            return;
        }
        server.getPlayerList().getPlayers().forEach(player -> {
            normalizeOnlineTime(player, now);
            deliverOnlineRewards(player);
        });
    }

    private static void deliverOnlineRewards(ServerPlayer player) {
        try {
            PersonalDateDeliveryResult result = PersonalDateRewardDispatcher.deliverOnline(
                    player, RewardManager.getCompensateDate(SakuraClock.serverNow()));
            if (result.changed()) {
                SakuraPlayerData.saveAndSync(player);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to deliver personal date rewards for {}",
                    player.getUUID(), failure);
        }
    }

    private static void normalizeOnlineTime(ServerPlayer player, java.util.Date date) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        java.time.LocalDate day = date.toInstant().atZone(
                java.time.ZoneId.systemDefault()).toLocalDate();
        if (SakuraOnlineTime.evaluate(player, data, day).isBaselineChanged()) {
            SakuraPlayerData.saveAndSync(player);
        }
    }

    private static boolean isConnected(MinecraftServer server, ServerPlayer player) {
        return server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
