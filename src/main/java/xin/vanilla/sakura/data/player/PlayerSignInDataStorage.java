package xin.vanilla.sakura.data.player;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 玩家签到数据存储类
 */
public class PlayerSignInDataStorage implements IStorage<IPlayerSignInData> {

    /**
     * 将玩家签到数据写入NBT标签
     */
    @Override
    public CompoundNBT writeNBT(Capability<IPlayerSignInData> capability, IPlayerSignInData instance, Direction side) {
        if (instance == null) {
            return new CompoundNBT();
        }
        CompoundNBT tag = new CompoundNBT();
        tag.putBoolean("notified", instance.isNotified());

        tag.putInt("totalSignInDays", instance.getTotalSignInDays());
        tag.putInt("continuousSignInDays", instance.calculateContinuousDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(instance.getLastSignInTime()));
        tag.putInt("signInCard", instance.getSignInCard());
        tag.putBoolean("autoRewarded", instance.isAutoRewarded());

        // 序列化签到记录
        ListNBT recordsNBT = new ListNBT();
        for (SignInRecord record : instance.getSignInRecords()) {
            recordsNBT.add(record.writeToNBT());
        }
        tag.put("signInRecords", recordsNBT);

        // 序列化CDK输入记录
        ListNBT cdkRecordsNBT = new ListNBT();
        for (KeyValue<String, KeyValue<Date, Boolean>> record : instance.getCdkRecords()) {
            CompoundNBT cdkRecordNBT = new CompoundNBT();
            cdkRecordNBT.putString("key", record.getKey());
            cdkRecordNBT.putString("date", DateUtils.toDateTimeString(record.getValue().getKey()));
            cdkRecordNBT.putBoolean("value", record.getValue().getValue());
            cdkRecordsNBT.add(cdkRecordNBT);
        }
        tag.put("cdkRecords", cdkRecordsNBT);
        return tag;
    }

    /**
     * 从NBT标签读取玩家签到数据
     */
    @Override
    public void readNBT(Capability<IPlayerSignInData> capability, IPlayerSignInData instance, Direction side, INBT nbt) {
        if (nbt instanceof CompoundNBT) {
            CompoundNBT nbtTag = (CompoundNBT) nbt;
            instance.setNotified(nbtTag.getBoolean("notified"));

            instance.setTotalSignInDays(nbtTag.getInt("totalSignInDays"));
            instance.setContinuousSignInDays(nbtTag.getInt("continuousSignInDays"));
            instance.setLastSignInTime(DateUtils.format(nbtTag.getString("lastSignInTime")));
            instance.setSignInCard(nbtTag.getInt("signInCard"));
            instance.setAutoRewarded(nbtTag.getBoolean("autoRewarded"));

            // 反序列化签到记录
            ListNBT recordsNBT = nbtTag.getList("signInRecords", 10); // 10 是 CompoundNBT 的类型ID
            List<SignInRecord> records = new ArrayList<>();
            for (int i = 0; i < recordsNBT.size(); i++) {
                records.add(SignInRecord.readFromNBT(recordsNBT.getCompound(i)));
            }
            instance.setSignInRecords(records);

            ListNBT cdkRecordsNBT = nbtTag.getList("cdkRecords", 10); // 10 是 CompoundNBT 的类型ID
            List<KeyValue<String, KeyValue<Date, Boolean>>> cdkRecords = new ArrayList<>();
            for (int i = 0; i < cdkRecordsNBT.size(); i++) {
                CompoundNBT cdkRecordNBT = cdkRecordsNBT.getCompound(i);
                cdkRecords.add(new KeyValue<>(cdkRecordNBT.getString("key")
                        , new KeyValue<>(DateUtils.format(cdkRecordNBT.getString("date"))
                        , cdkRecordNBT.getBoolean("value")))
                );
            }
            instance.setCdkRecords(cdkRecords);
        }
    }
}
