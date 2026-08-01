package xin.vanilla.sakura.config.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.LegacyRewardTypeIds;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 逐项读取旧 JSON 对象，避免 Gson 将同名概率键覆盖。
 */
public final class LegacyRewardConfigReader {
    private final Gson gson = new Gson();

    public RewardConfigDocument read(String json) throws IOException {
        RewardConfigDocument document = new RewardConfigDocument();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "baseRewards":
                        document.getGroups().add(new RewardGroup(
                                ERewardRule.BASE_REWARD, "base", readRewards(reader)));
                        break;
                    case "continuousRewards":
                        readGroups(reader, document, ERewardRule.CONTINUOUS_REWARD);
                        break;
                    case "cycleRewards":
                        readGroups(reader, document, ERewardRule.CYCLE_REWARD);
                        break;
                    case "yearRewards":
                        readGroups(reader, document, ERewardRule.YEAR_REWARD);
                        break;
                    case "monthRewards":
                        readGroups(reader, document, ERewardRule.MONTH_REWARD);
                        break;
                    case "weekRewards":
                        readGroups(reader, document, ERewardRule.WEEK_REWARD);
                        break;
                    case "dateTimeRewards":
                        readGroups(reader, document, ERewardRule.DATE_TIME_REWARD);
                        break;
                    case "cumulativeRewards":
                        readGroups(reader, document, ERewardRule.CUMULATIVE_REWARD);
                        break;
                    case "randomRewards":
                        readGroups(reader, document, ERewardRule.RANDOM_REWARD);
                        break;
                    case "cdkRewards":
                        readCdkGroups(reader, document);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            return document;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Invalid legacy reward configuration", e);
        }
    }

    private void readGroups(JsonReader reader, RewardConfigDocument document, ERewardRule rule)
            throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String key = normalizeKey(rule, reader.nextName());
            RewardList rewards = readRewards(reader);
            RewardGroup existing = rule == ERewardRule.RANDOM_REWARD
                    ? null
                    : findGroup(document, rule, key);
            if (existing == null) {
                document.getGroups().add(new RewardGroup(rule, key, rewards));
            } else {
                existing.getRewards().addAll(rewards);
            }
        }
        reader.endObject();
    }

    private void readCdkGroups(JsonReader reader, RewardConfigDocument document) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            JsonObject object = gson.fromJson(reader, JsonObject.class);
            if (object == null) {
                throw new IOException("CDK reward entry must be an object");
            }
            String key = stringValue(object, "key");
            String date = stringValue(object, "date");
            int limit = object.has("num") ? object.get("num").getAsInt() : 1;
            RewardList rewards = object.has("value")
                    ? readRewards(object.getAsJsonArray("value"))
                    : new RewardList();
            document.getGroups().add(RewardGroup.cdk(key, date, limit, rewards));
        }
        reader.endArray();
    }

    private RewardList readRewards(JsonReader reader) {
        JsonArray array = gson.fromJson(reader, JsonArray.class);
        return readRewards(array);
    }

    /** 读取早期已分组但仍使用枚举类型名的 schema v2 文档。 */
    public RewardConfigDocument readVersionedV2(String json) throws IOException {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("schemaVersion")
                    || root.get("schemaVersion").getAsInt() != 2) {
                throw new JsonParseException("Expected reward schema version 2");
            }
            JsonArray groups = root.getAsJsonArray("groups");
            if (groups == null) {
                throw new JsonParseException("Reward config groups are missing");
            }
            RewardConfigDocument document = new RewardConfigDocument();
            for (JsonElement element : groups) {
                JsonObject object = element.getAsJsonObject();
                ERewardRule rule = ERewardRule.valueOf(stringValue(object, "rule"));
                RewardGroup group = new RewardGroup(rule, stringValue(object, "key"),
                        readRewards(object.getAsJsonArray("rewards")));
                if (rule == ERewardRule.CDK_REWARD) {
                    group.setExpirationDate(stringValue(object, "expirationDate"));
                    group.setRedemptionLimit(object.has("redemptionLimit")
                            ? object.get("redemptionLimit").getAsInt() : 1);
                }
                document.getGroups().add(group);
            }
            return document;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Invalid schema v2 reward configuration", e);
        }
    }

    private RewardList readRewards(JsonArray array) {
        RewardList rewards = new RewardList();
        if (array == null) {
            return rewards;
        }
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            RewardTypeId typeId = LegacyRewardTypeIds.resolve(stringValue(json, "type"));
            JsonObject content = json.has("content")
                    ? gson.fromJson(json.getAsJsonObject("content"), JsonObject.class)
                    : new JsonObject();
            BigDecimal probability = json.has("probability")
                    ? json.get("probability").getAsBigDecimal()
                    : BigDecimal.ONE;
            Reward reward = new Reward(content, typeId, probability)
                    .setRewarded(json.has("rewarded") && json.get("rewarded").getAsBoolean())
                    .setDisabled(json.has("disabled") && json.get("disabled").getAsBoolean());
            rewards.add(reward);
        }
        return rewards;
    }

    private static String stringValue(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString()
                : "";
    }

    private static String normalizeKey(ERewardRule rule, String key) throws IOException {
        try {
            switch (rule) {
                case CONTINUOUS_REWARD:
                case CYCLE_REWARD:
                case YEAR_REWARD:
                case MONTH_REWARD:
                case WEEK_REWARD:
                case CUMULATIVE_REWARD:
                    return String.valueOf(Integer.parseInt(key));
                case RANDOM_REWARD:
                    return new BigDecimal(key).setScale(10, RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString();
                default:
                    return key;
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid legacy reward key for " + rule + ": " + key, e);
        }
    }

    private static RewardGroup findGroup(RewardConfigDocument document,
                                         ERewardRule rule, String key) {
        for (RewardGroup group : document.getGroups()) {
            if (group.getRule() == rule && group.getKey().equals(key)) {
                return group;
            }
        }
        return null;
    }
}
