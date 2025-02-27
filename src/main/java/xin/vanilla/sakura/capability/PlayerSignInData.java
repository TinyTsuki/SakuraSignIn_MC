package xin.vanilla.sakura.capability;

import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
            records.removeIf(Objects::isNull);
        }
        this.signInRecords = records;
    }

    @Override
    public @NonNull List<KeyValue<String, KeyValue<Date, Boolean>>> getCdkErrorRecords() {
        if (this.cdkRecords == null) {
            this.cdkRecords = new ArrayList<>();
        } else {
            this.cdkRecords.removeIf(Objects::isNull);
        }
        return this.cdkRecords;
    }

    @Override
    public void setCdkErrorRecords(List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords) {
        if (cdkRecords == null) {
            cdkRecords = new ArrayList<>();
        } else {
            cdkRecords.removeIf(Objects::isNull);
        }
        this.cdkRecords = cdkRecords;
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeInt(this.getTotalSignInDays());
        buffer.writeInt(this.getContinuousSignInDays());
        buffer.writeUtf(DateUtils.toDateTimeString(this.getLastSignInTime()));
        buffer.writeInt(this.getSignInCard());
        buffer.writeBoolean(this.isAutoRewarded());
        buffer.writeInt(this.getSignInRecords().size());
        for (SignInRecord record : this.getSignInRecords()) {
            buffer.writeNbt(record.writeToNBT());
        }
        buffer.writeInt(this.getCdkErrorRecords().size());
        for (KeyValue<String, KeyValue<Date, Boolean>> record : this.getCdkErrorRecords()) {
            buffer.writeUtf(record.getKey());
            buffer.writeUtf(DateUtils.toDateTimeString(record.getValue().getKey()));
            buffer.writeBoolean(record.getValue().getValue());
        }
    }

    public void readFromBuffer(FriendlyByteBuf buffer) {
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
        this.continuousSignInDays.set(capability.getContinuousSignInDays());
        this.lastSignInTime = capability.getLastSignInTime();
        this.signInCard.set(capability.getSignInCard());
        this.autoRewarded = capability.isAutoRewarded();
        this.setSignInRecords(capability.getSignInRecords());
        this.setCdkErrorRecords(capability.getCdkErrorRecords());
    }

    @Override
    public CompoundTag serializeNBT() {
        // 创建一个CompoundNBT对象，并将玩家的分数和活跃状态写入其中
        CompoundTag tag = new CompoundTag();
        tag.putInt("totalSignInDays", this.getTotalSignInDays());
        tag.putInt("continuousSignInDays", this.getContinuousSignInDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(this.getLastSignInTime()));
        tag.putInt("signInCard", this.getSignInCard());
        tag.putBoolean("autoRewarded", this.isAutoRewarded());
        // 序列化签到记录
        ListTag recordsNBT = new ListTag();
        for (SignInRecord record : this.getSignInRecords()) {
            recordsNBT.add(record.writeToNBT());
        }
        tag.put("signInRecords", recordsNBT);
        // 序列化CDK输错记录
        ListTag cdkRecordsNBT = new ListTag();
        for (KeyValue<String, KeyValue<Date, Boolean>> record : this.getCdkErrorRecords()) {
            CompoundTag cdkErrorRecordNBT = new CompoundTag();
            cdkErrorRecordNBT.putString("key", record.getKey());
            cdkErrorRecordNBT.putString("date", DateUtils.toDateTimeString(record.getValue().getKey()));
            cdkErrorRecordNBT.putBoolean("value", record.getValue().getValue());
        }
        tag.put("cdkRecords", cdkRecordsNBT);
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
        // 反序列化签到记录
        ListTag recordsNBT = nbt.getList("signInRecords", 10); // 10 是 CompoundNBT 的类型ID
        List<SignInRecord> records = new ArrayList<>();
        for (int i = 0; i < recordsNBT.size(); i++) {
            records.add(SignInRecord.readFromNBT(recordsNBT.getCompound(i)));
        }
        this.setSignInRecords(records);
        ListTag cdkRecordsNBT = nbt.getList("cdkRecords", 10); // 10 是 CompoundNBT 的类型ID
        List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords = new ArrayList<>();
        for (int i = 0; i < cdkRecordsNBT.size(); i++) {
            CompoundTag cdkErrorRecordNBT = cdkRecordsNBT.getCompound(i);
            cdkRecords.add(new KeyValue<>(cdkErrorRecordNBT.getString("key"), new KeyValue<>(DateUtils.format(cdkErrorRecordNBT.getString("date")), cdkErrorRecordNBT.getBoolean("value"))));
        }
        this.setCdkErrorRecords(cdkRecords);
    }

    @Override
    public void save(ServerPlayer player) {
        player.getCapability(PlayerSignInDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }
}
