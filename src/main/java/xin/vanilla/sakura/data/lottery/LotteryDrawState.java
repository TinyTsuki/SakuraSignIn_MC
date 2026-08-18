package xin.vanilla.sakura.data.lottery;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;

/** 每名玩家按奖池持久化的领取计数。 */
@Data
@NoArgsConstructor
public class LotteryDrawState {
    private String poolId = "";
    private String periodKey = "";
    private int periodDraws;
    private int totalDraws;
    private long lastDrawEpochMillis;

    public LotteryDrawState(String poolId) {
        this.poolId = poolId == null ? "" : poolId;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("poolId", poolId);
        tag.putString("periodKey", periodKey);
        tag.putInt("periodDraws", periodDraws);
        tag.putInt("totalDraws", totalDraws);
        tag.putLong("lastDrawEpochMillis", lastDrawEpochMillis);
        return tag;
    }

    public static LotteryDrawState deserializeNBT(CompoundTag tag) {
        LotteryDrawState state = new LotteryDrawState(tag.getString("poolId"));
        state.periodKey = tag.getString("periodKey");
        state.periodDraws = Math.max(0, tag.getInt("periodDraws"));
        state.totalDraws = Math.max(0, tag.getInt("totalDraws"));
        state.lastDrawEpochMillis = Math.max(0L, tag.getLong("lastDrawEpochMillis"));
        return state;
    }
}
