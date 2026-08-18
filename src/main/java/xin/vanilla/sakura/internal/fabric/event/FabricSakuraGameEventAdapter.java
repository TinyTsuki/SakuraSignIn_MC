package xin.vanilla.sakura.internal.fabric.event;

import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.common.util.BaniraScheduler;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.data.time.OnlineTimeRequirementResult;
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.data.time.SakuraOnlineTime;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.reward.personaldate.PersonalDateDeliveryResult;
import xin.vanilla.sakura.reward.personaldate.PersonalDateOnlineCheckSchedule;
import xin.vanilla.sakura.reward.personaldate.PersonalDateRewardDispatcher;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Fabric 原生回调转换为 Sakura 的稳定业务调用。
 */
public final class FabricSakuraGameEventAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int MAX_CLIENT_SETTINGS_ATTEMPTS = 120;
    private static final PersonalDateOnlineCheckSchedule ONLINE_REWARD_CHECK =
            new PersonalDateOnlineCheckSchedule(20L * 60L * 5L);

    private FabricSakuraGameEventAdapter() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                SignInCommand.register(dispatcher));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, replacement, alive) ->
                onPlayerCloned(original, replacement));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onPlayerLoggedIn(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SakuraPlayerData.removeServer(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(FabricSakuraGameEventAdapter::onServerTick);
    }

    /** 数据按 UUID 缓存，重生时只复制语言并同步新玩家实体。 */
    private static void onPlayerCloned(ServerPlayer original, ServerPlayer replacement) {
        SakuraPlayerData.sync(replacement);
    }

    /** 旧数据迁移失败时不得继续自动签到或删除旧数据。 */
    private static void onPlayerLoggedIn(ServerPlayer player) {
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
        Date now = RewardManager.getCompensateDate(SakuraClock.serverNow());
        normalizeOnlineTime(player, now);
        deliverOnlineRewards(player);
        waitForClientSettings(player, 0);
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
        Date now = RewardManager.getCompensateDate(SakuraClock.serverNow());
        LocalDate day = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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

    private static void onServerTick(MinecraftServer server) {
        Date now = RewardManager.getCompensateDate(SakuraClock.serverNow());
        LocalDate day = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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
            LOGGER.error("Unable to deliver personal date rewards for {}", player.getUUID(), failure);
        }
    }

    private static void normalizeOnlineTime(ServerPlayer player, Date date) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LocalDate day = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (SakuraOnlineTime.evaluate(player, data, day).isBaselineChanged()) {
            SakuraPlayerData.saveAndSync(player);
        }
    }

    private static boolean isConnected(MinecraftServer server, ServerPlayer player) {
        return server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
