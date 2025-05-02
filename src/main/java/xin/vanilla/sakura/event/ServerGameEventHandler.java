package xin.vanilla.sakura.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.ServerTimeSyncPacket;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Forge 事件处理
 */
@EventBusSubscriber(modid = SakuraSignIn.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.DEDICATED_SERVER)
public class ServerGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 同步客户端服务端数据
     */
    @SubscribeEvent
    public static void playerTickEvent(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 不用给未安装mod的玩家发送数据包
            if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString())) {
                // 同步玩家签到数据到客户端
                if (!SakuraSignIn.getPlayerCapabilityStatus().getOrDefault(player.getUUID().toString(), true)) {
                    // 如果玩家还活着则同步玩家传送数据到客户端
                    if (player.isAlive()) {
                        try {
                            PlayerDataAttachment.syncPlayerData(player);
                        } catch (Exception e) {
                            LOGGER.error("Failed to sync player data: ", e);
                        }
                    }
                }
                // 同步服务器时间到客户端
                player.connection.send(new ServerTimeSyncPacket());
            }
        }
    }

    /**
     * 玩家死亡后重生或者从末地回主世界
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        ServerPlayer original = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        original.revive();

        SakuraUtils.cloneServerPlayerLanguage(original, newPlayer);
        PlayerSignInData oldDataCap = PlayerDataAttachment.getData(original);
        PlayerSignInData newDataCap = PlayerDataAttachment.getData(newPlayer);
        newDataCap.copyFrom(oldDataCap);
        if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(newPlayer.getUUID().toString())) {
            SakuraSignIn.getPlayerCapabilityStatus().put(newPlayer.getUUID().toString(), false);
        }
    }

    /**
     * 玩家进入维度
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString())) {
                SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
            }
            SakuraSignIn.EXECUTOR_SERVICE.submit(() -> {
                // IDEA 又开始了
                for (int i = 0; i < 120 && player.getChatVisibility() == null; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        // 恢复中断状态
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Thread interrupted while waiting for player client settings.", e);
                        return;
                    }
                    LOGGER.debug("Waiting for player client setting: {}, {}.", SakuraUtils.getPlayerLanguage(player), i);
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Thread interrupted before processing sign in.", e);
                    return;
                }

                Objects.requireNonNull(player.getServer()).execute(() -> {
                    LOGGER.debug("Player language: {}.", SakuraUtils.getPlayerLanguage(player));
                    PlayerSignInData data = PlayerDataAttachment.getData(player);
                    if (ServerConfig.AUTO_SIGN_IN.get() && !RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
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

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退出服务器时移除键(移除mod安装状态)
        if (event.getEntity() instanceof ServerPlayer) {
            SakuraSignIn.getPlayerCapabilityStatus().remove(event.getEntity().getStringUUID());
        }
    }
}
