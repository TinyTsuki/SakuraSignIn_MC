package xin.vanilla.mc.event;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.capability.IPlayerSignInData;
import xin.vanilla.mc.capability.PlayerSignInDataCapability;
import xin.vanilla.mc.capability.PlayerSignInDataProvider;
import xin.vanilla.mc.config.ClientConfig;
import xin.vanilla.mc.config.RewardOptionDataManager;
import xin.vanilla.mc.config.ServerConfig;
import xin.vanilla.mc.enums.ESignInType;
import xin.vanilla.mc.network.*;
import xin.vanilla.mc.rewards.RewardManager;

import java.util.Date;

/**
 * Forge 事件处理
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean isPlayerLoggedIn = false;
    private static boolean hasTriggeredLoadComplete = false;

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.debug("Client: Player logged in.");
        isPlayerLoggedIn = true;
        // 同步客户端配置到服务器
        ModNetworkHandler.INSTANCE.send(new ClientConfigSyncPacket(), PacketDistributor.SERVER.noArg());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.debug("Client: Player logged out.");
        isPlayerLoggedIn = false;
        hasTriggeredLoadComplete = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && isPlayerLoggedIn) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.screen == null && !hasTriggeredLoadComplete) {
                LOGGER.debug("Client: Player load complete.");
                hasTriggeredLoadComplete = true;
                // 获取玩家的自定义数据
                IPlayerSignInData data = PlayerSignInDataCapability.getData(mc.player);
                // 服务器是否启用自动签到, 且玩家未签到
                if (ServerConfig.AUTO_SIGN_IN.get() && !RewardManager.isSignedIn(data, new Date(), true)) {
                    ModNetworkHandler.INSTANCE.send(new SignInPacket(new Date(), ClientConfig.AUTO_REWARDED.get(), ESignInType.SIGN_IN), PacketDistributor.SERVER.noArg());
                }
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
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();
        original.revive();
        LazyOptional<IPlayerSignInData> oldDataCap = original.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        LazyOptional<IPlayerSignInData> newDataCap = newPlayer.getCapability(PlayerSignInDataCapability.PLAYER_DATA);
        oldDataCap.ifPresent(oldData -> newDataCap.ifPresent(newData -> newData.copyFrom(oldData)));
        SakuraSignIn.getPlayerCapabilityStatus().put(newPlayer.getUUID().toString(), false);
    }

    /**
     * 玩家进入维度
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            Player player = (ServerPlayer) event.getEntity();
            SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 服务器端逻辑
        if (event.getEntity() instanceof ServerPlayer) {
            LOGGER.debug("Server: Player logged in.");
            // 同步玩家签到数据到客户端
            PlayerSignInDataCapability.syncPlayerData((ServerPlayer) event.getEntity());
            // 同步签到奖励配置到客户端
            for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket().split()) {
                ModNetworkHandler.INSTANCE.send(rewardOptionSyncPacket, PacketDistributor.PLAYER.with((ServerPlayer) event.getEntity()));
            }
            // 同步进度列表到客户端
            for (AdvancementPacket advancementPacket : new AdvancementPacket(((ServerPlayer) event.getEntity()).server.getAdvancements().getAllAdvancements()).split()) {
                ModNetworkHandler.INSTANCE.send(advancementPacket, PacketDistributor.PLAYER.with((ServerPlayer) event.getEntity()));
            }
        }
    }
}
