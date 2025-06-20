package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.function.Supplier;

/**
 * 通知服务器将奖励配置文件同步到指定客户端
 */
@Getter
public class RewardOptionRequestToServer {

    public RewardOptionRequestToServer() {
    }

    public RewardOptionRequestToServer(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(RewardOptionRequestToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                // 同步签到奖励配置到客户端
                for (RewardOptionSyncToBoth rewardOptionSyncToBoth : RewardConfigManager.toSyncPacket(player).split()) {
                    SakuraUtils.sendPacketToPlayer(rewardOptionSyncToBoth, player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
