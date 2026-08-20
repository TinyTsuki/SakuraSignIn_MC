package xin.vanilla.sakura.data;

import lombok.NonNull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家签到数据
 */
public class PlayerSignInData implements IPlayerSignInData {
    private final AtomicInteger totalSignInDays = new AtomicInteger();
    private final AtomicInteger continuousSignInDays = new AtomicInteger();
    private Date lastSignInTime;
    private final AtomicInteger signInCard = new AtomicInteger();
    private boolean autoRewarded;
    private List<SignInRecord> signInRecords;
    private Map<String, MonthSignInIndex> monthIndexes;
    // 兑换码:输入日期:是否有效
    private List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords;
    private List<PlayerPersonalDateSlot> personalDateSlots;
    private String onlineTimeBaselineDate = "";
    private int onlineTimeBaselineTicks;
    private List<LotteryDrawState> lotteryDrawStates = new ArrayList<>();
    private String language = "client";

    @Override
    public int getTotalSignInDays() {
        return this.totalSignInDays.get();
    }

    @Override
    public void setTotalSignInDays(int days) {
        this.totalSignInDays.set(days);
    }

    @Override
    public int plusTotalSignInDays() {
        return this.totalSignInDays.incrementAndGet();
    }

    @Override
    public int getContinuousSignInDays() {
        return this.continuousSignInDays.get();
    }

    @Override
    public void setContinuousSignInDays(int days) {
        this.continuousSignInDays.set(days);
    }

    @Override
    public int plusContinuousSignInDays() {
        return this.continuousSignInDays.incrementAndGet();
    }

    @Override
    public void resetContinuousSignInDays() {
        this.continuousSignInDays.set(1);
    }

    @Override
    public @NonNull Date getLastSignInTime() {
        return this.lastSignInTime = this.lastSignInTime == null ? DateUtils.getDate(0, 1, 1) : this.lastSignInTime;
    }

    @Override
    public void setLastSignInTime(Date time) {
        this.lastSignInTime = time;
    }

    @Override
    public int getSignInCard() {
        return this.signInCard.get();
    }

    @Override
    public int plusSignInCard() {
        return this.signInCard.incrementAndGet();
    }


    @Override
    public int plusSignInCard(int num) {
        return this.signInCard.addAndGet(num);
    }

    @Override
    public int subSignInCard() {
        return this.signInCard.decrementAndGet();
    }

    @Override
    public int subSignInCard(int num) {
        return this.signInCard.addAndGet(-num);
    }

    @Override
    public void setSignInCard(int num) {
        this.signInCard.set(num);
    }

    @Override
    public boolean isAutoRewarded() {
        return this.autoRewarded;
    }

    @Override
    public void setAutoRewarded(boolean autoRewarded) {
        this.autoRewarded = autoRewarded;
    }

    @Override
    public @NonNull List<SignInRecord> getSignInRecords() {
        if (this.signInRecords == null) {
            this.signInRecords = new ArrayList<>();
        } else {
            this.signInRecords.removeIf(Objects::isNull);
        }
        return this.signInRecords;
    }

    @Override
    public void setSignInRecords(List<SignInRecord> records) {
        if (records == null) {
            records = new ArrayList<>();
        } else {
            records = new ArrayList<>(records);
            records.removeIf(Objects::isNull);
        }
        this.signInRecords = records;
    }

    @Override
    public @NonNull Map<String, MonthSignInIndex> getMonthIndexes() {
        if (monthIndexes == null) {
            monthIndexes = new LinkedHashMap<>();
        }
        return monthIndexes;
    }

    @Override
    public void setMonthIndexes(Map<String, MonthSignInIndex> indexes) {
        monthIndexes = new LinkedHashMap<>();
        if (indexes != null) {
            indexes.forEach((month, index) ->
                    monthIndexes.put(month, MonthSignInIndex.deserializeNBT(index.serializeNBT())));
        }
    }

    @Override
    public boolean isSignedOn(Date date) {
        MonthDay monthDay = monthDay(date);
        MonthSignInIndex index = getMonthIndexes().get(monthDay.month);
        return index != null && index.isSigned(monthDay.day);
    }

    @Override
    public boolean isRewardedOn(Date date) {
        MonthDay monthDay = monthDay(date);
        MonthSignInIndex index = getMonthIndexes().get(monthDay.month);
        return index != null && index.isRewarded(monthDay.day);
    }

    @Override
    public void markSigned(Date date, boolean rewarded) {
        MonthDay monthDay = monthDay(date);
        getMonthIndexes()
                .computeIfAbsent(monthDay.month, MonthSignInIndex::new)
                .markSigned(monthDay.day, rewarded);
    }

    @Override
    public @NonNull List<KeyValue<String, KeyValue<Date, Boolean>>> getCdkRecords() {
        if (this.cdkRecords == null) {
            this.cdkRecords = new ArrayList<>();
        } else {
            this.cdkRecords.removeIf(Objects::isNull);
        }
        return this.cdkRecords;
    }

    @Override
    public void setCdkRecords(List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords) {
        if (cdkRecords == null) {
            cdkRecords = new ArrayList<>();
        } else {
            cdkRecords = new ArrayList<>(cdkRecords);
            cdkRecords.removeIf(Objects::isNull);
        }
        this.cdkRecords = cdkRecords;
    }

    @Override
    public @NonNull List<PlayerPersonalDateSlot> getPersonalDateSlots() {
        if (personalDateSlots == null) {
            personalDateSlots = new ArrayList<>();
        }
        personalDateSlots.removeIf(Objects::isNull);
        return personalDateSlots;
    }

    @Override
    public void setPersonalDateSlots(List<PlayerPersonalDateSlot> slots) {
        personalDateSlots = new ArrayList<>();
        if (slots != null) {
            slots.stream().filter(Objects::nonNull)
                    .map(slot -> PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT()))
                    .forEach(personalDateSlots::add);
        }
    }

    @Override
    public String getOnlineTimeBaselineDate() {
        return onlineTimeBaselineDate;
    }

    @Override
    public void setOnlineTimeBaselineDate(String date) {
        onlineTimeBaselineDate = date == null ? "" : date;
    }

    @Override
    public int getOnlineTimeBaselineTicks() {
        return onlineTimeBaselineTicks;
    }

    @Override
    public void setOnlineTimeBaselineTicks(int ticks) {
        onlineTimeBaselineTicks = Math.max(0, ticks);
    }

    @Override
    public @NonNull List<LotteryDrawState> getLotteryDrawStates() {
        lotteryDrawStates.removeIf(Objects::isNull);
        return lotteryDrawStates;
    }

    @Override
    public void setLotteryDrawStates(List<LotteryDrawState> states) {
        lotteryDrawStates = new ArrayList<>();
        if (states != null) {
            states.stream().filter(Objects::nonNull)
                    .map(state -> LotteryDrawState.deserializeNBT(state.serializeNBT()))
                    .forEach(lotteryDrawStates::add);
        }
    }

    @Override
    public String getLanguage() {
        return this.language;
    }

    @Override
    public void setLanguage(String language) {
        this.language = language;
    }

    @NonNull
    @Override
    public String getValidLanguage(@Nullable Player player) {
        return SakuraUtils.getValidLanguage(player, this.getLanguage());
    }

    public void copyFrom(IPlayerSignInData capability) {
        this.totalSignInDays.set(capability.getTotalSignInDays());
        this.continuousSignInDays.set(capability.calculateContinuousDays());
        this.lastSignInTime = capability.getLastSignInTime();
        this.signInCard.set(capability.getSignInCard());
        this.autoRewarded = capability.isAutoRewarded();
        this.language = capability.getLanguage();
        this.setMonthIndexes(capability.getMonthIndexes());
        this.setSignInRecords(capability.getSignInRecords());
        this.setCdkRecords(capability.getCdkRecords());
        this.setPersonalDateSlots(capability.getPersonalDateSlots());
        this.setOnlineTimeBaselineDate(capability.getOnlineTimeBaselineDate());
        this.setOnlineTimeBaselineTicks(capability.getOnlineTimeBaselineTicks());
        this.setLotteryDrawStates(capability.getLotteryDrawStates());
    }

    @Override
    public CompoundTag serializeNBT() {
        // 创建一个CompoundNBT对象，并将玩家的分数和活跃状态写入其中
        CompoundTag tag = new CompoundTag();
        tag.putInt("totalSignInDays", this.getTotalSignInDays());
        tag.putInt("continuousSignInDays", this.calculateContinuousDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(this.getLastSignInTime()));
        tag.putInt("signInCard", this.getSignInCard());
        tag.putBoolean("autoRewarded", this.isAutoRewarded());
        tag.putString("language", this.getLanguage());
        ListTag indexesNBT = new ListTag();
        getMonthIndexes().values().forEach(index -> indexesNBT.add(index.serializeNBT()));
        tag.put("monthIndexes", indexesNBT);

        // 序列化签到记录
        ListTag recordsNBT = new ListTag();
        for (SignInRecord record : this.getSignInRecords()) {
            recordsNBT.add(record.writeToNBT());
        }
        tag.put("signInRecords", recordsNBT);

        // 序列化CDK输入记录
        ListTag cdkRecordsNBT = new ListTag();
        for (KeyValue<String, KeyValue<Date, Boolean>> record : this.getCdkRecords()) {
            CompoundTag cdkRecordNBT = new CompoundTag();
            cdkRecordNBT.putString("key", record.key());
            cdkRecordNBT.putString("date", DateUtils.toDateTimeString(record.value().key()));
            cdkRecordNBT.putBoolean("value", record.value().value());
            cdkRecordsNBT.add(cdkRecordNBT);
        }
        tag.put("cdkRecords", cdkRecordsNBT);

        ListTag personalDateSlotsNBT = new ListTag();
        getPersonalDateSlots().forEach(slot -> personalDateSlotsNBT.add(slot.serializeNBT()));
        tag.put("personalDateSlots", personalDateSlotsNBT);
        tag.putString("onlineTimeBaselineDate", getOnlineTimeBaselineDate());
        tag.putInt("onlineTimeBaselineTicks", getOnlineTimeBaselineTicks());
        ListTag lotteryStatesNBT = new ListTag();
        getLotteryDrawStates().forEach(state -> lotteryStatesNBT.add(state.serializeNBT()));
        tag.put("lotteryDrawStates", lotteryStatesNBT);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // 从NBT标签中读取玩家的分数和活跃状态，并更新到实例中
        this.setTotalSignInDays(nbt.getInt("totalSignInDays"));
        this.setContinuousSignInDays(nbt.getInt("continuousSignInDays"));
        this.setLastSignInTime(DateUtils.format(nbt.getString("lastSignInTime")));
        this.setSignInCard(nbt.getInt("signInCard"));
        this.setAutoRewarded(nbt.getBoolean("autoRewarded"));
        this.setLanguage(nbt.getString("language"));
        Map<String, MonthSignInIndex> indexes = new LinkedHashMap<>();
        ListTag indexesNBT = nbt.getList("monthIndexes", 10);
        for (int i = 0; i < indexesNBT.size(); i++) {
            MonthSignInIndex index = MonthSignInIndex.deserializeNBT(indexesNBT.getCompound(i));
            indexes.put(index.getMonth(), index);
        }
        this.setMonthIndexes(indexes);

        // 反序列化签到记录
        ListTag recordsNBT = nbt.getList("signInRecords", 10); // 10 是 CompoundTag 的类型ID
        List<SignInRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsNBT.size(); i++) {
            records.add(SignInRecord.readFromNBT(recordsNBT.getCompound(i)));
        }
        this.setSignInRecords(records);
        ListTag cdkRecordsNBT = nbt.getList("cdkRecords", 10); // 10 是 CompoundTag 的类型ID
        List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords = new ArrayList<>();
        for (int i = 0; i < cdkRecordsNBT.size(); i++) {
            CompoundTag cdkRecordNBT = cdkRecordsNBT.getCompound(i);
            cdkRecords.add(new KeyValue<>(cdkRecordNBT.getString("key"), new KeyValue<>(DateUtils.format(cdkRecordNBT.getString("date")), cdkRecordNBT.getBoolean("value"))));
        }
        this.setCdkRecords(cdkRecords);

        List<PlayerPersonalDateSlot> slots = new ArrayList<>();
        ListTag slotsNBT = nbt.getList("personalDateSlots", 10);
        for (int i = 0; i < slotsNBT.size(); i++) {
            slots.add(PlayerPersonalDateSlot.deserializeNBT(slotsNBT.getCompound(i)));
        }
        this.setPersonalDateSlots(slots);
        this.setOnlineTimeBaselineDate(nbt.getString("onlineTimeBaselineDate"));
        this.setOnlineTimeBaselineTicks(nbt.getInt("onlineTimeBaselineTicks"));
        List<LotteryDrawState> lotteryStates = new ArrayList<>();
        ListTag lotteryStatesNBT = nbt.getList("lotteryDrawStates", 10);
        for (int i = 0; i < lotteryStatesNBT.size(); i++) {
            lotteryStates.add(LotteryDrawState.deserializeNBT(lotteryStatesNBT.getCompound(i)));
        }
        this.setLotteryDrawStates(lotteryStates);
    }

    public int calculateContinuousDays() {
        return getContinuousSignInDays();
    }

    @Override
    public int calculateContinuousDays(Date current) {
        LocalDate day = current.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int continuous = 0;
        int limit = Math.max(1, getTotalSignInDays());
        while (continuous < limit && isSignedOn(Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant()))) {
            continuous++;
            day = day.minusDays(1);
        }
        return continuous;
    }

    private static MonthDay monthDay(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("date");
        }
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return new MonthDay(
                String.format("%04d-%02d", localDate.getYear(), localDate.getMonthValue()),
                localDate.getDayOfMonth()
        );
    }

    private static final class MonthDay {
        private final String month;
        private final int day;

        private MonthDay(String month, int day) {
            this.month = month;
            this.day = day;
        }
    }
}
