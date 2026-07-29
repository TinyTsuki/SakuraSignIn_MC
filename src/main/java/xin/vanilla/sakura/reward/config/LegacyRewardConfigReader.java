package xin.vanilla.sakura.reward.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.rewards.RewardList;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 逐项读取旧 JSON 对象，避免 Gson 将同名概率键覆盖。
 */
public final class LegacyRewardConfigReader {
    private static final Type REWARD_LIST_TYPE = new TypeToken<RewardList>() {
    }.getType();

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
        } catch (JsonParseException | IllegalStateException e) {
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
                    ? gson.fromJson(object.get("value"), REWARD_LIST_TYPE)
                    : new RewardList();
            document.getGroups().add(RewardGroup.cdk(key, date, limit, rewards));
        }
        reader.endArray();
    }

    private RewardList readRewards(JsonReader reader) {
        RewardList rewards = gson.fromJson(reader, REWARD_LIST_TYPE);
        return rewards == null ? new RewardList() : rewards;
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
