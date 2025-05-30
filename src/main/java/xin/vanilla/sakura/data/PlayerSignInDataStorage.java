package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 玩家签到数据存储类，实现了IStorage接口，用于对玩家签到数据(IPlayerSignInData)的读写操作
 */
public class PlayerSignInDataStorage implements IStorage<IPlayerSignInData> {

    /**
     * 将玩家签到数据写入NBT标签
     *
     * @param capability 用于存储玩家签到数据的能力对象
     * @param instance   玩家签到数据实例
     * @param side       侧边标识，用于指定数据交换的方向
     * @return 返回包含玩家签到数据的CompoundNBT对象如果实例为null，则返回一个空的CompoundNBT对象
     */
    @Override
    public CompoundNBT writeNBT(Capability<IPlayerSignInData> capability, IPlayerSignInData instance, Direction side) {
        // 检查instance是否为null，如果是，则返回一个空的CompoundNBT对象，避免后续操作出错
        if (instance == null) {
            return new CompoundNBT();
        }
        // 创建一个CompoundNBT对象，并将玩家的分数和活跃状态写入其中
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("totalSignInDays", instance.getTotalSignInDays());
        tag.putInt("continuousSignInDays", instance.calculateContinuousDays());
        tag.putString("lastSignInTime", DateUtils.toDateTimeString(instance.getLastSignInTime()));
        tag.putInt("signInCard", instance.getSignInCard());
        tag.putBoolean("autoRewarded", instance.isAutoRewarded());
        tag.putString("language", instance.getLanguage());

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
     *
     * @param capability 用于存储玩家签到数据的能力对象
     * @param instance   玩家签到数据实例
     * @param side       侧边标识，用于指定数据交换的方向
     * @param nbt        包含玩家签到数据的NBT标签
     */
    @Override
    public void readNBT(Capability<IPlayerSignInData> capability, IPlayerSignInData instance, Direction side, INBT nbt) {
        // 检查nbt是否为CompoundNBT实例，如果不是，则不进行操作
        if (nbt instanceof CompoundNBT) {
            CompoundNBT nbtTag = (CompoundNBT) nbt;
            // 从NBT标签中读取玩家的分数和活跃状态，并更新到实例中
            instance.setTotalSignInDays(nbtTag.getInt("totalSignInDays"));
            instance.setContinuousSignInDays(nbtTag.getInt("continuousSignInDays"));
            instance.setLastSignInTime(DateUtils.format(nbtTag.getString("lastSignInTime")));
            instance.setSignInCard(nbtTag.getInt("signInCard"));
            instance.setAutoRewarded(nbtTag.getBoolean("autoRewarded"));
            instance.setLanguage(nbtTag.getString("language"));

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
