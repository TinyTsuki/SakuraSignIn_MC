package xin.vanilla.sakura.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.LogicalSide;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.CustomConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.data.player.PlayerSignInDataProvider;
import xin.vanilla.sakura.enums.EnumSignInType;
import xin.vanilla.sakura.network.packet.ServerTimeSyncToClient;
import xin.vanilla.sakura.network.packet.SignInToServer;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

public class EventHandlerProxy {
    private static final Logger LOGGER = LogManager.getLogger();

    private static long lastSaveConfTime = System.currentTimeMillis();
    private static long lastReadConfTime = System.currentTimeMillis();

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && SakuraSignIn.getServerInstance() != null
                && SakuraSignIn.getServerInstance().isRunning()
        ) {

            // 保存通用配置
            if (System.currentTimeMillis() - lastSaveConfTime >= 10 * 1000) {
                lastSaveConfTime = System.currentTimeMillis();
                CustomConfig.saveCustomConfig();
            }
            // 读取通用配置
            else if (System.currentTimeMillis() - lastReadConfTime >= 2 * 60 * 1000) {
                lastReadConfTime = System.currentTimeMillis();
                CustomConfig.loadCustomConfig(true);
            }
        }
    }

    /**
     * 玩家Tick事件
     */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.player;
            if (player.server.getTickCount() % 20 == 0) {
                // 给已安装mod玩家同步数据
                if (SakuraSignIn.getPlayerDataStatus().containsKey(SakuraUtils.getPlayerUUIDString(player))) {
                    // 是否未同步数据
                    if (!SakuraSignIn.getPlayerDataStatus().getOrDefault(SakuraUtils.getPlayerUUIDString(player), true)) {
                        // 若玩家未死亡
                        if (player.isAlive()) {
                            try {
                                PlayerSignInDataCapability.syncPlayerData(player);
                            } catch (Exception e) {
                                LOGGER.error("Failed to sync player data: ", e);
                            }
                        }
                    }
                    // 同步服务器时间到客户端
                    SakuraUtils.sendPacketToPlayer(new ServerTimeSyncToClient(), player);
                }
                // 检测服务器是否开启自动签到且玩家是否未签到
                if (player.isAlive()) {
                    if (ServerConfig.AUTO_SIGN_IN.get()) {
                        IPlayerSignInData data = PlayerSignInDataCapability.getData(player);
                        if (!RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
                            if (SakuraSignIn.getPlayerLanguageCache().containsKey(SakuraUtils.getPlayerUUIDString(player))) {
                                RewardManager.signIn(player, new SignInToServer(
                                        DateUtils.toDateTimeString(DateUtils.getServerDate()),
                                        data.isAutoRewarded(),
                                        EnumSignInType.SIGN_IN
                                ));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 能力附加事件
     */
    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            event.addCapability(SakuraSignIn.createResource("player_sign_in_data"), new PlayerSignInDataProvider());
        }
    }

    /**
     * 玩家死亡后重生或者从末地回主世界
     */
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        ServerPlayerEntity original = (ServerPlayerEntity) event.getOriginal();
        ServerPlayerEntity newPlayer = (ServerPlayerEntity) event.getPlayer();
        original.revive();

        SakuraUtils.cloneServerPlayerLanguage(original, newPlayer);
        LazyOptional<IPlayerSignInData> oldDataCap = original.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        LazyOptional<IPlayerSignInData> newDataCap = newPlayer.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        oldDataCap.ifPresent(oldData -> newDataCap.ifPresent(newData -> newData.copyFrom(oldData)));
        if (SakuraSignIn.getPlayerDataStatus().containsKey(SakuraUtils.getPlayerUUIDString(newPlayer))) {
            SakuraSignIn.getPlayerDataStatus().put(SakuraUtils.getPlayerUUIDString(newPlayer), false);
        }
    }

    /**
     * 玩家进入维度
     */
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            String uuid = SakuraUtils.getPlayerUUIDString(player);
            // 修改玩家数据状态为未同步(疑似玩家切换维度后能力数据会丢失)
            if (SakuraSignIn.getPlayerDataStatus().containsKey(uuid)) {
                SakuraSignIn.getPlayerDataStatus().put(uuid, false);
            }
        }
    }

    /**
     * 玩家登入事件
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    }

    /**
     * 玩家登出事件
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退出服务器时移除mod安装状态
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            SakuraSignIn.getPlayerDataStatus().remove(event.getPlayer().getStringUUID());
            SakuraSignIn.getPlayerLanguageCache().remove(event.getPlayer().getStringUUID());
        } else {
            PlayerEntity player = event.getPlayer();
            if (player.level.isClientSide) {
                ClientPlayerEntity clientPlayer = Minecraft.getInstance().player;
                if (clientPlayer != null && clientPlayer.getUUID().equals(player.getUUID())) {
                    LOGGER.debug("Current player has logged out.");
                    SakuraSignIn.setEnabled(false);
                }
            }
        }
    }
}
