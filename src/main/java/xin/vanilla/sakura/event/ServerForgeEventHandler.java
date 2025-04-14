package xin.vanilla.sakura.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInDataCapability;
import xin.vanilla.sakura.data.PlayerSignInDataProvider;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.packet.ServerTimeSyncPacket;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * Forge 事件处理
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class ServerForgeEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 同步客户端服务端数据
     */
    @SubscribeEvent
    public static void playerTickEvent(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerPlayer player = (ServerPlayer) event.player;
            // 不用给未安装mod的玩家发送数据包
            if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString())) {
                // 同步玩家签到数据到客户端
                if (!SakuraSignIn.getPlayerCapabilityStatus().getOrDefault(player.getUUID().toString(), true)) {
                    // 如果玩家还活着则同步玩家传送数据到客户端
                    if (player.isAlive()) {
                        try {
                            PlayerSignInDataCapability.syncPlayerData(player);
                        } catch (Exception e) {
                            LOGGER.error("Failed to sync player data: ", e);
                        }
                    }
                }
                // 同步服务器时间到客户端
                ModNetworkHandler.INSTANCE.send(new ServerTimeSyncPacket(), PacketDistributor.PLAYER.with(player));
            }
        }
    }

    /**
     * 当 AttachCapabilitiesEvent 事件发生时，此方法会为玩家实体附加自定义的能力
     * 在 Minecraft 中，实体可以拥有多种能力，这是一种扩展游戏行为的强大机制
     * 此处我们利用这个机制，为玩家实体附加一个用于签到的数据管理能力
     *
     * @param event 事件对象，包含正在附加能力的实体信息
     */
    @SubscribeEvent
    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        // 检查事件对象是否为玩家实体，因为我们的目标是为玩家附加能力
        if (event.getObject() instanceof Player) {
            // 为玩家实体附加一个名为 "player_sign_in_data" 的能力
            // 这个能力由 PlayerSignInDataProvider 提供，用于管理玩家的签到数据
            event.addCapability(new ResourceLocation(SakuraSignIn.MODID, "player_sign_in_data"), new PlayerSignInDataProvider());
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
        LazyOptional<IPlayerSignInData> oldDataCap = original.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        LazyOptional<IPlayerSignInData> newDataCap = newPlayer.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        oldDataCap.ifPresent(oldData -> newDataCap.ifPresent(newData -> newData.copyFrom(oldData)));
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
            // 获取玩家的自定义数据
            IPlayerSignInData data = PlayerSignInDataCapability.getData(player);
            // 服务器是否启用自动签到, 且玩家未签到
            if (ServerConfig.AUTO_SIGN_IN.get() && !RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
                RewardManager.signIn(player, new SignInPacket(DateUtils.toDateTimeString(DateUtils.getServerDate()), data.isAutoRewarded(), ESignInType.SIGN_IN));
            }
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

    // @SubscribeEvent
    // public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    //     // 服务器端逻辑
    //     if (event.getEntity() instanceof ServerPlayer) {
    //         LOGGER.debug("Server: Player logged in.");
    //         // 同步玩家签到数据到客户端
    //         PlayerSignInDataCapability.syncPlayerData((ServerPlayer) event.getEntity());
    //         // 同步签到奖励配置到客户端
    //         for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket().split()) {
    //             ModNetworkHandler.INSTANCE.send(rewardOptionSyncPacket, PacketDistributor.PLAYER.with((ServerPlayer) event.getEntity()));
    //         }
    //         // 同步进度列表到客户端
    //         for (AdvancementPacket advancementPacket : new AdvancementPacket(((ServerPlayer) event.getEntity()).server.getAdvancements().getAllAdvancements()).split()) {
    //             ModNetworkHandler.INSTANCE.send(advancementPacket, PacketDistributor.PLAYER.with((ServerPlayer) event.getEntity()));
    //         }
    //     }
    // }
}
