package xin.vanilla.sakura.event;

import xin.vanilla.sakura.config.CommonConfig;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.ServerTimeSyncPacket;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Forge 事件只负责把加载器事件转换为 Sakura 运行时调用。
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private ForgeEventHandler() {
    }

    @SubscribeEvent
    public static void playerTickEvent(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        SakuraNetwork.sendToPlayer(new ServerTimeSyncPacket(), player);
    }

    /**
     * 缓存按 UUID 管理，重生不再复制实体 Capability。
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayerEntity)
                || !(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity original = (ServerPlayerEntity) event.getOriginal();
        ServerPlayerEntity newPlayer = (ServerPlayerEntity) event.getPlayer();
        SakuraUtils.cloneServerPlayerLanguage(original, newPlayer);
        SakuraPlayerData.sync(newPlayer);
    }

    /**
     * 迁移必须先于自动签到，失败时不会删除旧节点或继续修改玩家数据。
     */
    @SubscribeEvent
    public static void onServerPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
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

        scheduleAutoSignIn(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            SakuraPlayerData.removeServer(event.getPlayer().getUUID());
        }
    }

    private static void scheduleAutoSignIn(ServerPlayerEntity player) {
        SakuraSignIn.EXECUTOR_SERVICE.submit(() -> {
            for (int i = 0; i < 120 && player.getChatVisibility() == null; i++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Thread interrupted while waiting for player client settings", interrupted);
                    return;
                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Thread interrupted before automatic sign-in", interrupted);
                return;
            }

            Objects.requireNonNull(player.getServer()).execute(() -> {
                IPlayerSignInData data = SakuraPlayerData.get(player);
                if (CommonConfig.get().server().autoSignIn()
                        && !RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
                    RewardManager.signIn(player, new SignInPacket(
                            DateUtils.toDateTimeString(DateUtils.getServerDate()),
                            data.isAutoRewarded(),
                            ESignInType.SIGN_IN
                    ));
                }
            });
        });
    }
}
