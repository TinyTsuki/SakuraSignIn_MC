package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardJsonCodec;

import java.util.ArrayList;
import java.util.List;

/** 服务端只发送已确定的中奖结果和纯展示候选序列。 */
@Getter
public final class LotteryRevealPacket implements INetworkPacket {
    private static final int MAX_PREVIEW = 64;
    private final String poolName;
    private final Reward winner;
    private final List<Reward> preview;

    public LotteryRevealPacket(String poolName, Reward winner, List<Reward> preview) {
        this.poolName = poolName == null ? "" : poolName;
        this.winner = winner == null ? Reward.getDefault() : winner.clone();
        this.preview = new ArrayList<>();
        if (preview != null) {
            preview.stream().limit(MAX_PREVIEW).forEach(reward -> this.preview.add(reward.clone()));
        }
    }

    public LotteryRevealPacket(BaniraPacketBuffer buffer) {
        poolName = buffer.readUtf();
        winner = RewardJsonCodec.decode(new com.google.gson.JsonParser().parse(buffer.readUtf()));
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PREVIEW) {
            throw new IllegalArgumentException("Invalid lottery preview count: " + count);
        }
        preview = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            preview.add(RewardJsonCodec.decode(
                    new com.google.gson.JsonParser().parse(buffer.readUtf())));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUtf(poolName);
        buffer.writeUtf(RewardJsonCodec.encode(winner).toString());
        buffer.writeVarInt(preview.size());
        preview.forEach(reward -> buffer.writeUtf(RewardJsonCodec.encode(reward).toString()));
    }

    public static void handle(LotteryRevealPacket packet, BaniraNetworkContext context) {
        context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        context.markHandled();
    }
}
