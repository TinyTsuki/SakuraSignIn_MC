package xin.vanilla.sakura.network.lottery;

import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.*;

public final class LotteryPacketFields {
    private LotteryPacketFields() { }

    public static LotteryQuoteRequest readRequest(BaniraPacketBuffer buffer) {
        return new LotteryQuoteRequest(buffer.readUuid(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(64), buffer.readInt());
    }
    public static void writeRequest(BaniraPacketBuffer buffer, LotteryQuoteRequest request) {
        buffer.writeUuid(request.getSession()); buffer.writeLong(request.getRequestId());
        buffer.writeLong(request.getGeneration()); buffer.writeUtf(request.getPoolId(), 64);
        buffer.writeInt(request.getCount());
    }
    public static Reward readReward(BaniraPacketBuffer buffer, Budget budget) {
        String json = buffer.readUtf(LotteryProtocolLimits.MAX_REWARD_CHARS);
        checkReward(json); budget.add(json);
        return RewardJsonCodec.decode(new com.google.gson.JsonParser().parse(json));
    }
    public static void writeReward(BaniraPacketBuffer buffer, Reward reward, Budget budget) {
        String json = RewardJsonCodec.encode(reward).toString();
        checkReward(json); budget.add(json);
        buffer.writeUtf(json, LotteryProtocolLimits.MAX_REWARD_BYTES);
    }
    public static void checkReward(String json) {
        LotteryProtocolLimits.text(json, LotteryProtocolLimits.MAX_REWARD_CHARS);
        LotteryProtocolLimits.json(json, LotteryProtocolLimits.MAX_REWARD_BYTES);
    }
    public static final class Budget {
        private int remaining;
        public Budget(int limit) { remaining = limit - 15; }
        public void add(String value) {
            int bytes = LotteryProtocolLimits.bytes(value) + 5;
            if (bytes > remaining) throw new IllegalArgumentException("Lottery packet exceeds byte budget");
            remaining -= bytes;
        }
    }
}
