package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.reward.lottery.LotteryDrawDispatcher;

/** 客户端动画结束后确认领取服务端已锁定的结果。 */
@Getter
public final class LotteryClaimPacket implements INetworkPacket {
    private final String token;

    public LotteryClaimPacket(String token) {
        this.token = token == null ? "" : token;
    }

    public LotteryClaimPacket(BaniraPacketBuffer buffer) {
        token = buffer.readUtf();
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUtf(token);
    }

    public static void handle(LotteryClaimPacket packet, BaniraNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.senderAs(ServerPlayer.class);
            if (sender != null && !packet.token.isEmpty()) {
                LotteryDrawDispatcher.claim(sender, packet.token);
            }
        });
        context.markHandled();
    }
}
