package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.lottery.LotteryPacketFields;
import xin.vanilla.sakura.reward.*;
import xin.vanilla.sakura.data.lottery.*;
import java.util.*;
import java.math.BigDecimal;

@Getter
public final class LotteryRevealPacket implements INetworkPacket {
    private final String operationId, poolName, token;
    private final List<Reward> winners, preview;
    private final LotteryPreviewMode previewMode;

    public LotteryRevealPacket(String operationId, String token, String poolName, List<Reward> winners,
                               List<Reward> preview, LotteryPreviewMode previewMode) {
        this.operationId = LotteryProtocolLimits.operationId(operationId);
        this.token = LotteryProtocolLimits.token(token);
        this.poolName = LotteryProtocolLimits.text(poolName, 96);
        if (previewMode == null || winners == null || winners.isEmpty() || winners.size() > 100
                || preview == null || preview.size() > 64 || !previewMode.showsItems() && !preview.isEmpty())
            throw new IllegalArgumentException("Invalid lottery reveal");
        this.previewMode = previewMode;
        this.winners = copy(winners, true);
        this.preview = copy(preview, !previewMode.showsProbabilities());
        checkBudget();
    }

    public LotteryRevealPacket(BaniraPacketBuffer buffer) {
        operationId = LotteryProtocolLimits.operationId(buffer.readUtf(56));
        token = LotteryProtocolLimits.token(buffer.readUtf(36));
        poolName = LotteryProtocolLimits.text(buffer.readUtf(96), 96);
        LotteryPacketFields.Budget budget = budget();
        int count = buffer.readVarInt();
        if (count < 1 || count > 100) throw new IllegalArgumentException("Invalid lottery winner count");
        List<Reward> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(LotteryPacketFields.readReward(buffer, budget));
        winners = copy(values, true);
        count = buffer.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid lottery preview count");
        values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(LotteryPacketFields.readReward(buffer, budget));
        previewMode = buffer.readEnum(LotteryPreviewMode.class);
        if (previewMode == null || !previewMode.showsItems() && !values.isEmpty())
            throw new IllegalArgumentException("Unexpected hidden lottery preview");
        preview = copy(values, !previewMode.showsProbabilities());
    }

    public List<Reward> getWinners() { return copy(winners, false); }
    public List<Reward> getPreview() { return copy(preview, false); }

    public void toBytes(BaniraPacketBuffer buffer) {
        checkBudget();
        LotteryPacketFields.Budget budget = budget();
        buffer.writeUtf(operationId, 56); buffer.writeUtf(token, 36); buffer.writeUtf(poolName, 384);
        buffer.writeVarInt(winners.size());
        winners.forEach(reward -> LotteryPacketFields.writeReward(buffer, reward, budget));
        buffer.writeVarInt(preview.size());
        preview.forEach(reward -> LotteryPacketFields.writeReward(buffer, reward, budget));
        buffer.writeEnum(previewMode);
    }

    public static void handle(LotteryRevealPacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            Object origin = context.connectionKey();
            if (origin != null) context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet, origin));
        }
        context.markHandled();
    }

    public Reward getWinner() { return winners.get(0).clone(); }
    public boolean isPreviewVisible() { return previewMode.showsItems(); }

    private LotteryPacketFields.Budget budget() {
        LotteryPacketFields.Budget budget = new LotteryPacketFields.Budget(LotteryProtocolLimits.MAX_REVEAL_BYTES);
        budget.add(operationId); budget.add(token); budget.add(poolName); return budget;
    }
    private void checkBudget() {
        LotteryPacketFields.Budget budget = budget();
        for (Reward reward : winners) check(reward, budget);
        for (Reward reward : preview) check(reward, budget);
    }
    private static void check(Reward reward, LotteryPacketFields.Budget budget) {
        String json = RewardJsonCodec.encode(reward).toString();
        LotteryPacketFields.checkReward(json); budget.add(json);
    }
    private static List<Reward> copy(List<Reward> source, boolean hideProbability) {
        List<Reward> copy = new ArrayList<>();
        for (Reward reward : source) {
            if (reward == null) throw new IllegalArgumentException("Missing lottery reward");
            Reward value = reward.clone();
            if (hideProbability) value.setProbability(BigDecimal.ONE);
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }
}
