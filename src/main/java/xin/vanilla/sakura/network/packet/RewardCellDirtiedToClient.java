package xin.vanilla.sakura.network.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.network.ClientProxy;

import java.util.function.Supplier;

public class RewardCellDirtiedToClient {

    public RewardCellDirtiedToClient() {
    }

    public RewardCellDirtiedToClient(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(RewardCellDirtiedToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientProxy::handleRewardCellDirtied));
        ctx.get().setPacketHandled(true);
    }
}
