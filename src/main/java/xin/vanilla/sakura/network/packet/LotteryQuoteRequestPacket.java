package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.data.lottery.LotteryQuoteRequest;
import xin.vanilla.sakura.network.lottery.*;

@Getter
public final class LotteryQuoteRequestPacket implements INetworkPacket {
    private final LotteryQuoteRequest request;
    public LotteryQuoteRequestPacket(LotteryQuoteRequest request) {
        if (request == null) throw new IllegalArgumentException("Lottery request required");
        this.request = request;
    }
    public LotteryQuoteRequestPacket(BaniraPacketBuffer buffer) { this(LotteryPacketFields.readRequest(buffer)); }
    public void toBytes(BaniraPacketBuffer buffer) { LotteryPacketFields.writeRequest(buffer, request); }
    public static void handle(LotteryQuoteRequestPacket packet, BaniraNetworkContext context) {
        if (context.isServerSide()) context.enqueueWork(() -> LotteryPacketServerHandlers.quote(packet.request, context));
        context.markHandled();
    }
}
