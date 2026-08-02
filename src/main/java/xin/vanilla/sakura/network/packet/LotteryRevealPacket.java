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
    private static final int MAX_WINNERS = 100;
    private static final int MAX_PREVIEW = 64;
    private final String poolName;
    private final List<Reward> winners;
    private final List<Reward> preview;
    private final boolean previewVisible;

    public LotteryRevealPacket(String poolName, List<Reward> winners,
                               List<Reward> preview, boolean previewVisible) {
        this.poolName = poolName == null ? "" : poolName;
        this.winners = cloneRewards(winners, MAX_WINNERS);
        this.preview = new ArrayList<>();
        if (preview != null) {
            preview.stream().limit(MAX_PREVIEW).forEach(reward -> this.preview.add(reward.clone()));
        }
        this.previewVisible = previewVisible;
    }

    public LotteryRevealPacket(BaniraPacketBuffer buffer) {
        poolName = buffer.readUtf();
        int winnerCount = buffer.readVarInt();
        if (winnerCount < 1 || winnerCount > MAX_WINNERS) {
            throw new IllegalArgumentException("Invalid lottery winner count: " + winnerCount);
        }
        winners = new ArrayList<>();
        for (int i = 0; i < winnerCount; i++) {
            winners.add(RewardJsonCodec.decode(
                    new com.google.gson.JsonParser().parse(buffer.readUtf())));
        }
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PREVIEW) {
            throw new IllegalArgumentException("Invalid lottery preview count: " + count);
        }
        preview = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            preview.add(RewardJsonCodec.decode(
                    new com.google.gson.JsonParser().parse(buffer.readUtf())));
        }
        previewVisible = buffer.readBoolean();
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUtf(poolName);
        buffer.writeVarInt(winners.size());
        winners.forEach(reward -> buffer.writeUtf(RewardJsonCodec.encode(reward).toString()));
        buffer.writeVarInt(preview.size());
        preview.forEach(reward -> buffer.writeUtf(RewardJsonCodec.encode(reward).toString()));
        buffer.writeBoolean(previewVisible);
    }

    public static void handle(LotteryRevealPacket packet, BaniraNetworkContext context) {
        context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        context.markHandled();
    }

    public Reward getWinner() {
        return winners.get(0);
    }

    private static List<Reward> cloneRewards(List<Reward> source, int limit) {
        List<Reward> result = new ArrayList<>();
        if (source != null) {
            source.stream().filter(java.util.Objects::nonNull).limit(limit)
                    .forEach(reward -> result.add(reward.clone()));
        }
        if (result.isEmpty()) {
            result.add(Reward.getDefault());
        }
        return result;
    }
}
