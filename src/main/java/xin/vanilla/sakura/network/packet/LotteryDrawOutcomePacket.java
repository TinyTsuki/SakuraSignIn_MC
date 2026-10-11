package xin.vanilla.sakura.network.packet;

import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.sakura.data.lottery.LotteryProtocolLimits;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;

public final class LotteryDrawOutcomePacket implements INetworkPacket {
    private final String operationId;
    public LotteryDrawOutcomePacket(String operationId) { this.operationId = LotteryProtocolLimits.operationId(operationId); }
    public LotteryDrawOutcomePacket(BaniraPacketBuffer buffer) { this(buffer.readUtf(56)); }
    public String getOperationId() { return operationId; }
    public void toBytes(BaniraPacketBuffer buffer) { buffer.writeUtf(operationId, 56); }
    public static void handle(LotteryDrawOutcomePacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            Object origin = context.connectionKey();
            if (origin != null) context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet, origin));
        }
        context.markHandled();
    }
}
