package xin.vanilla.sakura.config.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.RewardJsonCodec;
import xin.vanilla.sakura.reward.RewardList;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class RewardRuleHistory {
    private RewardRuleHistory() {
    }

    static String encode(RewardConfig config, ERewardRule rule) throws IOException {
        RewardConfig scoped = new RewardConfig();
        copyRule(config, scoped, rule);
        JsonObject history = new JsonObject();
        history.addProperty("historyVersion", 1);
        history.addProperty("historyRule", rule.name());
        history.add("configuration", new JsonParser().parse(new RewardConfigCodec().encode(scoped)));
        return history.toString();
    }

    static Snapshot decode(String json, ERewardRule rule) throws IOException {
        try {
            JsonObject object = new JsonParser().parse(json).getAsJsonObject();
            if (object.has("historyVersion")) {
                if (object.get("historyVersion").getAsInt() != 1
                        || !rule.name().equals(object.get("historyRule").getAsString())) {
                    throw new IOException("Mismatched reward history rule or version");
                }
                RewardConfig config = new RewardConfigCodec().decode(object.get("configuration").toString());
                return new Snapshot(rule, config, null);
            }
            Map<String, RewardList> rewards = new Gson().fromJson(json,
                    new TypeToken<LinkedHashMap<String, RewardList>>() {
                    }.getType());
            if (rewards == null || rewards.containsValue(null)) throw new IOException("Invalid reward history");
            for (RewardList list : rewards.values()) {
                list.forEach(RewardJsonCodec::encode);
            }
            RewardConfig legacy = new RewardConfig();
            RewardConfigManager.setRewardMap(legacy, rule, rewards);
            new RewardConfigCodec().encode(legacy);
            return new Snapshot(rule, null, rewards);
        } catch (RuntimeException e) {
            throw new IOException("Invalid reward history", e);
        }
    }

    private static void copyRule(RewardConfig source, RewardConfig target, ERewardRule rule) {
        if (rule == ERewardRule.PERSONAL_DATE_REWARD) {
            target.getPersonalDatePresets().clear();
            target.getPersonalDatePresets().addAll(source.getPersonalDatePresets());
        } else if (rule == ERewardRule.LOTTERY_REWARD) {
            target.getLotteryPools().clear();
            target.getLotteryPools().addAll(source.getLotteryPools());
        } else {
            RewardConfigManager.setRewardMap(target, rule, RewardConfigManager.getRewardMap(source, rule));
        }
    }

    static final class Snapshot {
        private final ERewardRule rule;
        private final RewardConfig config;
        private final Map<String, RewardList> rewards;

        private Snapshot(ERewardRule rule, RewardConfig config, Map<String, RewardList> rewards) {
            this.rule = rule;
            this.config = config;
            this.rewards = rewards;
        }

        void restore(RewardConfig config) {
            if (this.config != null) copyRule(this.config, config, rule);
            else RewardConfigManager.setRewardMap(config, rule, rewards);
        }

        Map<String, RewardList> rewards() {
            return config == null ? rewards : RewardConfigManager.getRewardMap(config, rule);
        }
    }
}
