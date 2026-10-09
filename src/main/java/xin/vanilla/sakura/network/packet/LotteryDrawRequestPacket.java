package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.lottery.*;
import java.util.UUID;

@Getter
public final class LotteryDrawRequestPacket implements INetworkPacket {
    private final LotteryDrawRequest request;
    public LotteryDrawRequestPacket(LotteryDrawRequest request) {
        if (request == null) throw new IllegalArgumentException("Lottery draw required");
        this.request = request;
    }
    public LotteryDrawRequestPacket(UUID session, long operationId, long generation,
                                    String poolId, int count, String token) {
        this(new LotteryDrawRequest(session, operationId, generation, poolId, count, token));
    }
    public LotteryDrawRequestPacket(BaniraPacketBuffer buffer) {
        LotteryQuoteRequest header = LotteryPacketFields.readRequest(buffer);
        request = new LotteryDrawRequest(header.getSession(), header.getRequestId(),
                header.getGeneration(), header.getPoolId(), header.getCount(), buffer.readUtf(36));
    }
    public void toBytes(BaniraPacketBuffer buffer) {
        LotteryPacketFields.writeRequest(buffer, request.getRequest()); buffer.writeUtf(request.getQuoteToken(), 36);
    }
    public static void handle(LotteryDrawRequestPacket packet, BaniraNetworkContext context) {
        if (context.isServerSide()) context.enqueueWork(() -> LotteryPacketServerHandlers.draw(packet.request, context));
        context.markHandled();
    }
}
