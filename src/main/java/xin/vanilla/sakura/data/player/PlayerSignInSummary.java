package xin.vanilla.sakura.data.player;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.util.SakuraNbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 永久摘要不依赖可清理的月度详情，签到判定与累计统计始终可用。
 */
@Data
@NoArgsConstructor
public class PlayerSignInSummary {
    public static final int SCHEMA_VERSION = 1;

    private int totalSignInDays;
    private int continuousSignInDays;
    private String lastSignInTime = "";
    private int signInCard;
    private boolean autoRewarded;
    private String language = "client";
    private ListTag cdkRecords = new ListTag();
    private Map<String, MonthSignInIndex> monthIndexes = new TreeMap<>();
    private List<PlayerPersonalDateSlot> personalDateSlots = new ArrayList<>();
    private String onlineTimeBaselineDate = "";
    private int onlineTimeBaselineTicks;
    private List<LotteryDrawState> lotteryDrawStates = new ArrayList<>();
    private boolean legacyCapabilityMigrated;
    private String legacyCapabilityBackup = "";

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        tag.putInt("totalSignInDays", totalSignInDays);
        tag.putInt("continuousSignInDays", continuousSignInDays);
        tag.putString("lastSignInTime", lastSignInTime);
        tag.putInt("signInCard", signInCard);
        tag.putBoolean("autoRewarded", autoRewarded);
        tag.putString("language", language);
        tag.put("cdkRecords", copyList(cdkRecords));

        ListTag indexes = new ListTag();
        monthIndexes.values().forEach(index -> indexes.add(index.serializeNBT()));
        tag.put("monthIndexes", indexes);

        ListTag slots = new ListTag();
        personalDateSlots.stream().filter(java.util.Objects::nonNull)
                .forEach(slot -> slots.add(slot.serializeNBT()));
        tag.put("personalDateSlots", slots);
        tag.putString("onlineTimeBaselineDate", onlineTimeBaselineDate);
        tag.putInt("onlineTimeBaselineTicks", onlineTimeBaselineTicks);
        ListTag lotteryStates = new ListTag();
        lotteryDrawStates.stream().filter(java.util.Objects::nonNull)
                .forEach(state -> lotteryStates.add(state.serializeNBT()));
        tag.put("lotteryDrawStates", lotteryStates);

        CompoundTag migration = new CompoundTag();
        migration.putBoolean("legacyCapabilityMigrated", legacyCapabilityMigrated);
        migration.putString("legacyCapabilityBackup", legacyCapabilityBackup);
        tag.put("migration", migration);
        return tag;
    }

    public static PlayerSignInSummary deserializeNBT(CompoundTag tag) {
        PlayerSignInSummary summary = new PlayerSignInSummary();
        summary.totalSignInDays = tag.getIntOr("totalSignInDays", 0);
        summary.continuousSignInDays = tag.getIntOr("continuousSignInDays", 0);
        summary.lastSignInTime = tag.getStringOr("lastSignInTime", "");
        summary.signInCard = tag.getIntOr("signInCard", 0);
        summary.autoRewarded = tag.getBooleanOr("autoRewarded", false);
        summary.language = tag.getStringOr("language", "client");
        summary.cdkRecords = copyList(SakuraNbt.compoundListOrEmpty(tag, "cdkRecords"));

        ListTag indexes = SakuraNbt.compoundListOrEmpty(tag, "monthIndexes");
        for (int i = 0; i < indexes.size(); i++) {
            MonthSignInIndex index = MonthSignInIndex.deserializeNBT(indexes.getCompoundOrEmpty(i));
            summary.monthIndexes.put(index.getMonth(), index);
        }

        ListTag slots = SakuraNbt.compoundListOrEmpty(tag, "personalDateSlots");
        for (int i = 0; i < slots.size(); i++) {
            summary.personalDateSlots.add(
                    PlayerPersonalDateSlot.deserializeNBT(slots.getCompoundOrEmpty(i)));
        }
        summary.onlineTimeBaselineDate = tag.getStringOr("onlineTimeBaselineDate", "");
        summary.onlineTimeBaselineTicks = Math.max(0, tag.getIntOr("onlineTimeBaselineTicks", 0));
        ListTag lotteryStates = SakuraNbt.compoundListOrEmpty(tag, "lotteryDrawStates");
        for (int i = 0; i < lotteryStates.size(); i++) {
            summary.lotteryDrawStates.add(
                    LotteryDrawState.deserializeNBT(lotteryStates.getCompoundOrEmpty(i)));
        }

        CompoundTag migration = tag.getCompoundOrEmpty("migration");
        summary.legacyCapabilityMigrated = migration.getBooleanOr("legacyCapabilityMigrated", false);
        summary.legacyCapabilityBackup = migration.getStringOr("legacyCapabilityBackup", "");
        return summary;
    }

    public static boolean isCurrentSchema(CompoundTag tag) {
        return tag.getIntOr("schemaVersion", 0) == SCHEMA_VERSION;
    }

    private static ListTag copyList(ListTag source) {
        ListTag copy = new ListTag();
        for (Tag element : source) {
            copy.add(element.copy());
        }
        return copy;
    }
}
