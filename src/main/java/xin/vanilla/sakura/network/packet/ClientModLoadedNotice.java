package xin.vanilla.sakura.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.RewardOptionDataManager;
import xin.vanilla.sakura.data.PlayerSignInDataCapability;
import xin.vanilla.sakura.network.ModNetworkHandler;

import java.util.function.Supplier;

public class ClientModLoadedNotice {

    public ClientModLoadedNotice() {
    }

    public ClientModLoadedNotice(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(ClientModLoadedNotice packet, Supplier<NetworkEvent.Context> ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.get().enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
                // 同步玩家签到数据到客户端
                PlayerSignInDataCapability.syncPlayerData(player);
                // 同步签到奖励配置到客户端
                for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(player).split()) {
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
