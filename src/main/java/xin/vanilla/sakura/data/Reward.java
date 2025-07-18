package xin.vanilla.sakura.data;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.Component;

import java.io.Serializable;
import java.math.BigDecimal;

import static xin.vanilla.sakura.rewards.RewardConfigManager.GSON;


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
    private EnumRewardType type = EnumRewardType.NONE;
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

    public <T> Reward(T content, EnumRewardType type) {
        this.content = RewardManager.serializeReward(content, type);
        this.type = type;
    }

    public Reward(JsonObject content, EnumRewardType type) {
        this.content = content;
        this.type = type;
    }

    public Reward(JsonObject content, EnumRewardType type, BigDecimal probability) {
        this.content = content;
        this.type = type;
        this.probability = probability;
    }

    public <T> Reward(T content, EnumRewardType type, BigDecimal probability) {
        this.content = RewardManager.serializeReward(content, type);
        this.type = type;
        this.probability = probability;
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

    public Text getName(String languageCode) {
        return new Text(getName(languageCode, true));
    }

    public Component getName(String languageCode, boolean withNum) {
        return RewardManager.getRewardName(languageCode, this, withNum);
    }

    public static Reward getDefault() {
        return new Reward(RewardManager.serializeReward(new ItemStack(Items.AIR), EnumRewardType.ITEM), EnumRewardType.ITEM);
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        if (this.rewarded) {
            json.addProperty("rewarded", this.rewarded);
        }
        if (this.disabled) {
            json.addProperty("disabled", this.disabled);
        }
        json.addProperty("type", this.type.name());
        json.addProperty("probability", this.probability);
        json.add("content", this.content);
        return json;
    }
}
