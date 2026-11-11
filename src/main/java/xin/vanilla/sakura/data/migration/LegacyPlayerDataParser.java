package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.util.SakuraNbt;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 将旧 Forge Capability 拆成永久摘要与按月详情。
 */
public final class LegacyPlayerDataParser {

    public LegacyPlayerData parse(CompoundTag legacy) {
        PlayerSignInSummary summary = new PlayerSignInSummary();
        summary.setTotalSignInDays(legacy.getIntOr("totalSignInDays", 0));
        summary.setContinuousSignInDays(legacy.getIntOr("continuousSignInDays", 0));
        summary.setLastSignInTime(legacy.getStringOr("lastSignInTime", ""));
        summary.setSignInCard(legacy.getIntOr("signInCard", 0));
        summary.setAutoRewarded(legacy.getBooleanOr("autoRewarded", false));
        summary.setLanguage(legacy.getStringOr("language", "client"));
        summary.setCdkRecords(copyList(SakuraNbt.compoundListOrEmpty(legacy, "cdkRecords")));
        summary.setOnlineTimeBaselineDate(legacy.getStringOr("onlineTimeBaselineDate", ""));
        summary.setOnlineTimeBaselineTicks(Math.max(0, legacy.getIntOr("onlineTimeBaselineTicks", 0)));
        ListTag lotteryStates = SakuraNbt.compoundListOrEmpty(legacy, "lotteryDrawStates");
        for (int i = 0; i < lotteryStates.size(); i++) {
            summary.getLotteryDrawStates().add(
                    LotteryDrawState.deserializeNBT(lotteryStates.getCompoundOrEmpty(i)));
        }
        ListTag indexes = SakuraNbt.compoundListOrEmpty(legacy, "monthIndexes");
        for (int i = 0; i < indexes.size(); i++) {
            MonthSignInIndex index = MonthSignInIndex.deserializeNBT(indexes.getCompoundOrEmpty(i));
            summary.getMonthIndexes().put(index.getMonth(), index);
        }
        ListTag slots = SakuraNbt.compoundListOrEmpty(legacy, "personalDateSlots");
        for (int i = 0; i < slots.size(); i++) {
            summary.getPersonalDateSlots().add(
                    PlayerPersonalDateSlot.deserializeNBT(slots.getCompoundOrEmpty(i)));
        }

        Map<String, List<CompoundTag>> recordsByMonth = new LinkedHashMap<>();
        ListTag records = SakuraNbt.compoundListOrEmpty(legacy, "signInRecords");
        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = records.getCompoundOrEmpty(i);
            LocalDate day = parseDay(record.getStringOr("compensateTime", ""));
            if (day == null) {
                recordsByMonth.computeIfAbsent("unknown", key -> new ArrayList<>()).add(record.copy());
                continue;
            }
            String month = String.format("%04d-%02d", day.getYear(), day.getMonthValue());
            summary.getMonthIndexes()
                    .computeIfAbsent(month, MonthSignInIndex::new)
                    .markSigned(day.getDayOfMonth(), record.getBooleanOr("rewarded", false));
            recordsByMonth.computeIfAbsent(month, key -> new ArrayList<>()).add(record.copy());
        }
        return new LegacyPlayerData(summary, recordsByMonth);
    }

    private static LocalDate parseDay(String value) {
        Date date = DateUtils.format(value);
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static ListTag copyList(ListTag source) {
        ListTag copy = new ListTag();
        for (Tag element : source) {
            copy.add(element.copy());
        }
        return copy;
    }
}
