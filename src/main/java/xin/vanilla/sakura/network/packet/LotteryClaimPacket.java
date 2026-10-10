package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.data.lottery.LotteryProtocolLimits;
import xin.vanilla.sakura.reward.lottery.LotteryDrawDispatcher;

@Getter
public final class LotteryClaimPacket implements INetworkPacket {
    private final String operationId;
    private final String token;
    public LotteryClaimPacket(String operationId, String token) {
        this.operationId = LotteryProtocolLimits.operationId(operationId);
        this.token = LotteryProtocolLimits.token(token);
    }
    public LotteryClaimPacket(BaniraPacketBuffer buffer) { this(buffer.readUtf(56), buffer.readUtf(36)); }
    public void toBytes(BaniraPacketBuffer buffer) { buffer.writeUtf(operationId, 56); buffer.writeUtf(token, 36); }
    public static void handle(LotteryClaimPacket packet, BaniraNetworkContext context) {
        Object origin = context.connectionKey();
        if (context.isServerSide() && origin != null) context.enqueueWork(() -> {
            ServerPlayer sender = context.senderAs(ServerPlayer.class);
            if (xin.vanilla.sakura.reward.lottery.LotteryRewardService.isCurrent(sender, origin))
                LotteryDrawDispatcher.acknowledge(sender, packet.operationId, packet.token);
        });
        context.markHandled();
    }
}
