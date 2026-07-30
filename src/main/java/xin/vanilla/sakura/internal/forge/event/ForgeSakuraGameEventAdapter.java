package xin.vanilla.sakura.internal.forge.event;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.common.util.BaniraScheduler;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 Forge 原生事件转换为 Sakura 业务调用。
 */
public final class ForgeSakuraGameEventAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int MAX_CLIENT_SETTINGS_ATTEMPTS = 120;

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
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        SignInCommand.register(event.getDispatcher());
    }

    /**
     * 数据按 UUID 缓存，重生时只复制语言并同步新玩家实体。
     */
    private static void onPlayerCloned(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayerEntity)
                || !(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity original = (ServerPlayerEntity) event.getOriginal();
        ServerPlayerEntity replacement = (ServerPlayerEntity) event.getPlayer();
        SakuraUtils.cloneServerPlayerLanguage(original, replacement);
        SakuraPlayerData.sync(replacement);
    }

    /**
     * 旧数据迁移失败时不得继续自动签到或删除旧数据。
     */
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
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
        waitForClientSettings(player, 0);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            SakuraPlayerData.removeServer(event.getPlayer().getUUID());
        }
    }

    private static void waitForClientSettings(ServerPlayerEntity player, int attempt) {
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

    private static void runAutomaticSignIn(MinecraftServer server, ServerPlayerEntity player) {
        if (!isConnected(server, player)) {
            return;
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (CommonConfig.get().server().autoSignIn()
                && !RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
            RewardManager.signIn(player, new SignInPacket(
                    DateUtils.toDateTimeString(DateUtils.getServerDate()),
                    data.isAutoRewarded(),
                    ESignInType.SIGN_IN
            ));
        }
    }

    private static boolean isConnected(MinecraftServer server, ServerPlayerEntity player) {
        return server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
