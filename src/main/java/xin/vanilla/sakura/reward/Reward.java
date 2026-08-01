package xin.vanilla.sakura.reward;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.experimental.Accessors;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.enums.ERewardType;

import java.io.Serializable;
import java.math.BigDecimal;

import static xin.vanilla.sakura.config.reward.RewardConfigManager.GSON;


/**
 * 奖励实体
 */
@Data
@Accessors(chain = true)
public class Reward implements Cloneable, Serializable {
    /**
     * 奖励是否领取
     */
    private boolean rewarded;
    /**
     * 奖励是否禁用
     */
    private boolean disabled;
    /**
     * 奖励类型
     */
    private RewardTypeId typeId;
    /**
     * 奖励概率
     */
    private BigDecimal probability = BigDecimal.ONE;
    /**
     * 奖励内容
     */
    private JsonObject content;

    public Reward() {
    }

    public BigDecimal getProbability() {
        return probability.compareTo(BigDecimal.ONE) <= 0 || probability.compareTo(BigDecimal.ZERO) > 0 ? probability : BigDecimal.ONE;
    }

    public <T> Reward(T content, RewardTypeId typeId) {
        this.content = RewardOperations.encode(typeId, content);
        this.typeId = typeId;
    }

    /**
     * 迁移期仅供尚未切换到注册表 ID 的内部调用使用，最终删除旧枚举时一并移除。
     */
    public <T> Reward(T content, ERewardType type) {
        this(content, legacyTypeId(type));
    }

    public Reward(JsonObject content, RewardTypeId typeId) {
        this.content = content;
        this.typeId = typeId;
    }

    public Reward(JsonObject content, ERewardType type) {
        this(content, legacyTypeId(type));
    }

    public Reward(JsonObject content, RewardTypeId typeId, BigDecimal probability) {
        this.content = content;
        this.typeId = typeId;
        this.probability = probability;
    }

    public Reward(JsonObject content, ERewardType type, BigDecimal probability) {
        this(content, legacyTypeId(type), probability);
    }

    public <T> Reward(T content, RewardTypeId typeId, BigDecimal probability) {
        this.content = RewardOperations.encode(typeId, content);
        this.typeId = typeId;
        this.probability = probability;
    }

    public <T> Reward(T content, ERewardType type, BigDecimal probability) {
        this(content, legacyTypeId(type), probability);
    }

    public ERewardType getType() {
        if (SakuraRewardTypes.ITEM.equals(typeId)) return ERewardType.ITEM;
        if (SakuraRewardTypes.EFFECT.equals(typeId)) return ERewardType.EFFECT;
        if (SakuraRewardTypes.EXPERIENCE_POINT.equals(typeId)) return ERewardType.EXP_POINT;
        if (SakuraRewardTypes.EXPERIENCE_LEVEL.equals(typeId)) return ERewardType.EXP_LEVEL;
        if (SakuraRewardTypes.SIGN_IN_CARD.equals(typeId)) return ERewardType.SIGN_IN_CARD;
        if (SakuraRewardTypes.ADVANCEMENT.equals(typeId)) return ERewardType.ADVANCEMENT;
        if (SakuraRewardTypes.MESSAGE.equals(typeId)) return ERewardType.MESSAGE;
        if (SakuraRewardTypes.COMMAND.equals(typeId)) return ERewardType.COMMAND;
        return null;
    }

    public Reward setType(ERewardType type) {
        this.typeId = legacyTypeId(type);
        return this;
    }

    @Override
    public Reward clone() {
        try {
            Reward cloned = (Reward) super.clone();
            cloned.content = GSON.fromJson(GSON.toJson(this.content), JsonObject.class);
            return cloned;
        } catch (Exception e) {
            return new Reward();
        }
    }

    public Component getName(String languageCode, boolean withNum) {
        return RewardOperations.describe(languageCode, this, withNum);
    }

    public static Reward getDefault() {
        JsonObject content = new JsonObject();
        content.addProperty("item", "minecraft:air");
        content.addProperty("count", 0);
        return new Reward(content, SakuraRewardTypes.ITEM);
    }

    public JsonObject toJsonObject() {
        return RewardJsonCodec.encode(this);
    }

    private static RewardTypeId legacyTypeId(ERewardType type) {
        return type.rewardTypeId();
    }
}
