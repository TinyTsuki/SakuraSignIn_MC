package xin.vanilla.sakura.data;

import lombok.Data;
import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.RewardListJsonCodec;
import xin.vanilla.banira.common.util.CollectionUtils;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.Serializable;
import java.util.Date;

import static xin.vanilla.sakura.config.reward.RewardConfigManager.GSON;

/**
 * 签到记录
 */
@Data
public class SignInRecord implements Serializable, Cloneable {
    private static final Logger LOGGER = LogManager.getLogger();
    /**
     * 补偿后时间(签到时间+签到冷却刷新时间)
     */
    @NonNull
    private Date compensateTime;
    /**
     * 签到时间
     */
    @NonNull
    private Date signInTime;
    /**
     * 签到玩家
     */
    @NonNull
    private String signInUUID;
    /**
     * 奖励是否领取
     */
    private boolean rewarded;
    /**
     * 签到物品奖励
     */
    @NonNull
    private RewardList rewardList;

    public SignInRecord() {
        this.compensateTime = new Date();
        this.signInTime = new Date();
        this.signInUUID = "";
        this.rewardList = new RewardList();
    }


    // 序列化到 NBT
    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("compensateTime", DateUtils.toDateTimeString(compensateTime));
        tag.putString("signInTime", DateUtils.toDateTimeString(signInTime));
        tag.putString("signInUUID", signInUUID);
        tag.putBoolean("rewarded", rewarded);
        tag.putString("rewardList", GSON.toJson(RewardListJsonCodec.encode(rewardList)));
        return tag;
    }

    // 反序列化方法
    public static SignInRecord readFromNBT(CompoundTag tag) {
        SignInRecord record = new SignInRecord();
        // 读取简单字段
        record.compensateTime = DateUtils.format(tag.getString("compensateTime"));
        record.signInTime = DateUtils.format(tag.getString("signInTime"));
        record.signInUUID = tag.getString("signInUUID");
        record.rewarded = tag.getBoolean("rewarded");

        // 反序列化奖励列表
        String rewardListString = tag.getString("rewardList");
        record.rewardList = RewardListJsonCodec.decodeLenient(rewardListString,
                exception -> LOGGER.warn("Skipping malformed sign-in reward entry for {}",
                        record.signInTime, exception));
        return record;
    }

    @Override
    public SignInRecord clone() {
        try {
            SignInRecord cloned = (SignInRecord) super.clone();
            cloned.compensateTime = (Date) this.compensateTime.clone();
            cloned.signInTime = (Date) this.signInTime.clone();
            if (CollectionUtils.isNotNullOrEmpty(this.rewardList))
                cloned.rewardList = this.rewardList.clone();
            else
                cloned.rewardList = new RewardList();
            return cloned;
        } catch (Exception e) {
            return new SignInRecord();
        }
    }
}
