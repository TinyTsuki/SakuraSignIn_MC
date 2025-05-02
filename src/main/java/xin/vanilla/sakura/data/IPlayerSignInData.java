package xin.vanilla.sakura.data;

import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;
import xin.vanilla.sakura.config.KeyValue;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

/**
 * 玩家签到数据
 */
public interface IPlayerSignInData extends INBTSerializable<CompoundTag> {
    // TIPS 加完属性记得去 PlayerSignInDataStorage 里注册

    /**
     * 获取累计签到天数
     */
    int getTotalSignInDays();

    /**
     * 设置累计签到天数
     */
    void setTotalSignInDays(int days);

    /**
     * 递增累计签到天数
     */
    int plusTotalSignInDays();

    /**
     * 获取连续签到天数
     */
    int getContinuousSignInDays();

    /**
     * 设置连续签到天数
     */
    void setContinuousSignInDays(int days);

    /**
     * 增加连续签到天数
     */
    int plusContinuousSignInDays();

    /**
     * 重置连续签到天数
     */
    void resetContinuousSignInDays();

    /**
     * 获取最后签到时间
     */
    Date getLastSignInTime();

    /**
     * 设置最后签到时间
     */
    void setLastSignInTime(Date time);

    /**
     * 获取补签卡数量
     */
    int getSignInCard();

    /**
     * 增加补签卡数量
     */
    int plusSignInCard();

    /**
     * 增加补签卡数量
     */
    int plusSignInCard(int num);

    /**
     * 减少补签卡数量
     */
    int subSignInCard();

    /**
     * 减少补签卡数量
     */
    int subSignInCard(int num);

    /**
     * 设置补签卡数量
     */
    void setSignInCard(int num);

    /**
     * 是否自动领取奖励
     */
    boolean isAutoRewarded();

    /**
     * 设置是否自动领取奖励
     */
    void setAutoRewarded(boolean autoRewarded);

    /**
     * 获取签到记录
     */
    @NonNull
    List<SignInRecord> getSignInRecords();

    /**
     * 设置签到记录
     */
    void setSignInRecords(List<SignInRecord> records);

    /**
     * 获取CDK输入记录
     */
    @NonNull
    List<KeyValue<String, KeyValue<Date, Boolean>>> getCdkRecords();

    /**
     * 设置CDK输入记录
     */
    void setCdkRecords(List<KeyValue<String, KeyValue<Date, Boolean>>> records);

    /**
     * 获取语言
     */
    String getLanguage();

    /**
     * 设置语言
     */
    void setLanguage(String language);

    /**
     * 获取有效的语言
     */
    @NonNull
    String getValidLanguage(@Nullable Player player);

    void writeToBuffer(FriendlyByteBuf buffer);

    void readFromBuffer(FriendlyByteBuf buffer);

    void copyFrom(IPlayerSignInData capability);

    void save(ServerPlayer player);

    int calculateContinuousDays();
}
