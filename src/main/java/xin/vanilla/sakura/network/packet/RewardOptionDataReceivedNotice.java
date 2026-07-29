package xin.vanilla.sakura.network.packet;

import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;

public class RewardOptionDataReceivedNotice implements INetworkPacket {
    private final boolean success;

    public RewardOptionDataReceivedNotice(boolean success) {
        this.success = success;
    }

    public RewardOptionDataReceivedNotice(BaniraPacketBuffer buf) {
        this.success = buf.readBoolean();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeBoolean(this.success);
    }

    public static void handle(RewardOptionDataReceivedNotice packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> SakuraClientPacketHandlers.handleRewardUploadResult(packet.success));
        ctx.markHandled();
    }
}
