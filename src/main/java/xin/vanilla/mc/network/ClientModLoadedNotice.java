package xin.vanilla.mc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.capability.PlayerSignInDataCapability;
import xin.vanilla.mc.config.RewardOptionDataManager;

import java.util.function.Supplier;

public class ClientModLoadedNotice {

    public ClientModLoadedNotice() {
    }

    public ClientModLoadedNotice(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(ClientModLoadedNotice packet, Supplier<NetworkEvent.Context> ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.get().enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
                // 同步玩家签到数据到客户端
                PlayerSignInDataCapability.syncPlayerData(player);
                // 同步签到奖励配置到客户端
                for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(player.hasPermissions(3)).split()) {
                    ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket);
                }
                // 同步进度列表到客户端
                for (AdvancementPacket advancementPacket : new AdvancementPacket((player).server.getAdvancements().getAllAdvancements()).split()) {
                    ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), advancementPacket);
                }
            }
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.get().setPacketHandled(true);
    }
}
