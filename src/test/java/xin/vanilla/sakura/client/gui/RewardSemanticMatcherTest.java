package xin.vanilla.sakura.client.gui;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.reward.Reward;

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 验证双击选择使用奖励语义身份，而不是数量或概率等可变量。
 */
public class RewardSemanticMatcherTest {
    @Test
    public void itemsIgnoreCountAndProbabilityButKeepItemAndNbtIdentity() {
        Reward first = reward(ERewardType.ITEM,
                content("item", "minecraft:apple", "count", 1, "nbt", "{CustomModelData:1}"), "0.2");
        Reward same = reward(ERewardType.ITEM,
                content("item", "minecraft:apple", "count", 64, "nbt", "{CustomModelData:1}"), "1");
        Reward differentNbt = reward(ERewardType.ITEM,
                content("item", "minecraft:apple", "count", 1, "nbt", "{CustomModelData:2}"), "0.2");

        assertTrue(RewardSemanticMatcher.matches(first, same));
        assertFalse(RewardSemanticMatcher.matches(first, differentNbt));
    }

    @Test
    public void effectsIgnoreDurationAmplifierAndProbability() {
        Reward first = reward(ERewardType.EFFECT,
                content("effect", "minecraft:luck", "duration", 20, "amplifier", 0), "0.1");
        Reward same = reward(ERewardType.EFFECT,
                content("effect", "minecraft:luck", "duration", 1200, "amplifier", 4), "1");
        Reward different = reward(ERewardType.EFFECT,
                content("effect", "minecraft:speed", "duration", 20, "amplifier", 0), "0.1");

        assertTrue(RewardSemanticMatcher.matches(first, same));
        assertFalse(RewardSemanticMatcher.matches(first, different));
    }

    @Test
    public void commandsUseExactContentAndNumericRewardsUseTheirType() {
        assertTrue(RewardSemanticMatcher.matches(
                reward(ERewardType.COMMAND, content("command", "/say hi"), "0.2"),
                reward(ERewardType.COMMAND, content("command", "/say hi"), "1")));
        assertFalse(RewardSemanticMatcher.matches(
                reward(ERewardType.COMMAND, content("command", "/say hi"), "1"),
                reward(ERewardType.COMMAND, content("command", "/say bye"), "1")));
        assertTrue(RewardSemanticMatcher.matches(
                reward(ERewardType.EXP_POINT, content("expPoint", 1), "0.3"),
                reward(ERewardType.EXP_POINT, content("expPoint", 200), "1")));
    }

    @Test
    public void messageAndAdvancementRewardsUseExactSemanticContent() {
        assertTrue(RewardSemanticMatcher.matches(
                reward(ERewardType.ADVANCEMENT,
                        content("advancement", "minecraft:story/root"), "0.2"),
                reward(ERewardType.ADVANCEMENT,
                        content("advancement", "minecraft:story/root"), "1")));
        assertFalse(RewardSemanticMatcher.matches(
                reward(ERewardType.ADVANCEMENT,
                        content("advancement", "minecraft:story/root"), "1"),
                reward(ERewardType.ADVANCEMENT,
                        content("advancement", "minecraft:story/mine_stone"), "1")));
        assertTrue(RewardSemanticMatcher.matches(
                reward(ERewardType.MESSAGE, content("text", "hello"), "0.2"),
                reward(ERewardType.MESSAGE, content("text", "hello"), "1")));
        assertFalse(RewardSemanticMatcher.matches(
                reward(ERewardType.MESSAGE, content("text", "hello"), "1"),
                reward(ERewardType.MESSAGE, content("text", "bye"), "1")));
    }

    @Test
    public void malformedIdentityFallsBackToCompleteContent() {
        assertTrue(RewardSemanticMatcher.matches(
                reward(ERewardType.ITEM, content("bad", "same"), "0.2"),
                reward(ERewardType.ITEM, content("bad", "same"), "1")));
        assertFalse(RewardSemanticMatcher.matches(
                reward(ERewardType.ITEM, content("bad", "first"), "1"),
                reward(ERewardType.ITEM, content("bad", "second"), "1")));
    }

    private static Reward reward(ERewardType type, JsonObject content, String probability) {
        return new Reward(content, type, new BigDecimal(probability));
    }

    private static JsonObject content(Object... values) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < values.length; i += 2) {
            String key = (String) values[i];
            Object value = values[i + 1];
            if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else {
                json.addProperty(key, String.valueOf(value));
            }
        }
        return json;
    }
}
