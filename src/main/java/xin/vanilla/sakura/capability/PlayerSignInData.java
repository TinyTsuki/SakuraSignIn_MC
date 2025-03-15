package xin.vanilla.sakura.capability;

import lombok.NonNull;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    // 兑换码:输入日期:是否有效
    private List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords;

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

    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeInt(this.getTotalSignInDays());
        buffer.writeInt(this.calculateContinuousDays());
        buffer.writeUtf(DateUtils.toDateTimeString(this.getLastSignInTime()));
        buffer.writeInt(this.getSignInCard());
        buffer.writeBoolean(this.isAutoRewarded());
        buffer.writeInt(this.getSignInRecords().size());
        for (SignInRecord record : this.getSignInRecords()) {
            buffer.writeNbt(record.writeToNBT());
        }
        buffer.writeInt(this.getCdkRecords().size());
        for (KeyValue<String, KeyValue<Date, Boolean>> record : this.getCdkRecords()) {
            buffer.writeUtf(record.getKey());
            buffer.writeUtf(DateUtils.toDateTimeString(record.getValue().getKey()));
            buffer.writeBoolean(record.getValue().getValue());
        }
    }

    public void readFromBuffer(PacketBuffer buffer) {
        this.totalSignInDays.set(buffer.readInt());
        this.continuousSignInDays.set(buffer.readInt());
        this.lastSignInTime = DateUtils.format(buffer.readUtf());
        this.signInCard.set(buffer.readInt());
        this.autoRewarded = buffer.readBoolean();
        this.signInRecords = new ArrayList<>();
        for (int i = 0; i < buffer.readInt(); i++) {
            this.signInRecords.add(SignInRecord.readFromNBT(Objects.requireNonNull(buffer.readNbt())));
        }
        this.cdkRecords = new ArrayList<>();
        for (int i = 0; i < buffer.readInt(); i++) {
            this.cdkRecords.add(new KeyValue<>(buffer.readUtf(), new KeyValue<>(DateUtils.format(buffer.readUtf()), buffer.readBoolean())));
        }
    }

    public void copyFrom(IPlayerSignInData capability) {
        this.totalSignInDays.set(capability.getTotalSignInDays());
        this.continuousSignInDays.set(capability.calculateContinuousDays());
        this.lastSignInTime = capability.getLastSignInTime();
        this.signInCard.set(capability.getSignInCard());
        this.autoRewarded = capability.isAutoRewarded();
        this.setSignInRecords(capability.getSignInRecords());
        this.setCdkRecords(capability.getCdkRecords());
    }

    @Override
    public CompoundNBT serializeNBT() {
        // 创建一个CompoundNBT对象，并将玩家的分数和活跃状态写入其中
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("totalSignInDays", this.getTotalSignInDays());
        tag.putInt("continuousSignInDays", this.calculateContinuousDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(this.getLastSignInTime()));
        tag.putInt("signInCard", this.getSignInCard());
        tag.putBoolean("autoRewarded", this.isAutoRewarded());
        // 序列化签到记录
        ListNBT recordsNBT = new ListNBT();
        for (SignInRecord record : this.getSignInRecords()) {
            recordsNBT.add(record.writeToNBT());
        }
        tag.put("signInRecords", recordsNBT);
        // 序列化CDK输入记录
        ListNBT cdkRecordsNBT = new ListNBT();
        for (KeyValue<String, KeyValue<Date, Boolean>> record : this.getCdkRecords()) {
            CompoundNBT cdkRecordNBT = new CompoundNBT();
            cdkRecordNBT.putString("key", record.getKey());
            cdkRecordNBT.putString("date", DateUtils.toDateTimeString(record.getValue().getKey()));
            cdkRecordNBT.putBoolean("value", record.getValue().getValue());
            cdkRecordsNBT.add(cdkRecordNBT);
        }
        tag.put("cdkRecords", cdkRecordsNBT);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        // 从NBT标签中读取玩家的分数和活跃状态，并更新到实例中
        this.setTotalSignInDays(nbt.getInt("totalSignInDays"));
        this.setContinuousSignInDays(nbt.getInt("continuousSignInDays"));
        this.setLastSignInTime(DateUtils.format(nbt.getString("lastSignInTime")));
        this.setSignInCard(nbt.getInt("signInCard"));
        this.setAutoRewarded(nbt.getBoolean("autoRewarded"));
        // 反序列化签到记录
        ListNBT recordsNBT = nbt.getList("signInRecords", 10); // 10 是 CompoundNBT 的类型ID
        List<SignInRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsNBT.size(); i++) {
            records.add(SignInRecord.readFromNBT(recordsNBT.getCompound(i)));
        }
        this.setSignInRecords(records);
        ListNBT cdkRecordsNBT = nbt.getList("cdkRecords", 10); // 10 是 CompoundNBT 的类型ID
        List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords = new ArrayList<>();
        for (int i = 0; i < cdkRecordsNBT.size(); i++) {
            CompoundNBT cdkRecordNBT = cdkRecordsNBT.getCompound(i);
            cdkRecords.add(new KeyValue<>(cdkRecordNBT.getString("key"), new KeyValue<>(DateUtils.format(cdkRecordNBT.getString("date")), cdkRecordNBT.getBoolean("value"))));
        }
        this.setCdkRecords(cdkRecords);
    }

    @Override
    public void save(ServerPlayerEntity player) {
        player.getCapability(PlayerSignInDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }

    public int calculateContinuousDays() {
        try {
            return DateUtils.calculateContinuousDays(this.getSignInRecords().stream().map(SignInRecord::getCompensateTime).collect(Collectors.toList())
                    , RewardManager.getCompensateDate(DateUtils.getServerDate()));
        } catch (Exception e) {
            return 0;
        }
    }
}
