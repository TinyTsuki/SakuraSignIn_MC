package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.banira.common.util.DateUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将旧 Forge Capability 拆成永久摘要与按月详情。
 */
public final class LegacyPlayerDataParser {

    public LegacyPlayerData parse(CompoundTag legacy) {
        PlayerSignInSummary summary = new PlayerSignInSummary();
        summary.setTotalSignInDays(legacy.getInt("totalSignInDays"));
        summary.setContinuousSignInDays(legacy.getInt("continuousSignInDays"));
        summary.setLastSignInTime(legacy.getString("lastSignInTime"));
        summary.setSignInCard(legacy.getInt("signInCard"));
        summary.setAutoRewarded(legacy.getBoolean("autoRewarded"));
        summary.setLanguage(legacy.contains("language", 8) ? legacy.getString("language") : "client");
        summary.setCdkRecords(copyList(legacy.getList("cdkRecords", 10)));
        summary.setOnlineTimeBaselineDate(legacy.getString("onlineTimeBaselineDate"));
        summary.setOnlineTimeBaselineTicks(Math.max(0, legacy.getInt("onlineTimeBaselineTicks")));
        ListTag lotteryStates = legacy.getList("lotteryDrawStates", 10);
        for (int i = 0; i < lotteryStates.size(); i++) {
            summary.getLotteryDrawStates().add(
                    LotteryDrawState.deserializeNBT(lotteryStates.getCompound(i)));
        }
        ListTag indexes = legacy.getList("monthIndexes", 10);
        for (int i = 0; i < indexes.size(); i++) {
            MonthSignInIndex index = MonthSignInIndex.deserializeNBT(indexes.getCompound(i));
            summary.getMonthIndexes().put(index.getMonth(), index);
        }
        ListTag slots = legacy.getList("personalDateSlots", 10);
        for (int i = 0; i < slots.size(); i++) {
            summary.getPersonalDateSlots().add(
                    PlayerPersonalDateSlot.deserializeNBT(slots.getCompound(i)));
        }

        Map<String, List<CompoundTag>> recordsByMonth = new LinkedHashMap<>();
        ListTag records = legacy.getList("signInRecords", 10);
        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = records.getCompound(i);
            LocalDate day = parseDay(record.getString("compensateTime"));
            if (day == null) {
                recordsByMonth.computeIfAbsent("unknown", key -> new ArrayList<>()).add(record.copy());
                continue;
            }
            String month = String.format("%04d-%02d", day.getYear(), day.getMonthValue());
            summary.getMonthIndexes()
                    .computeIfAbsent(month, MonthSignInIndex::new)
                    .markSigned(day.getDayOfMonth(), record.getBoolean("rewarded"));
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
