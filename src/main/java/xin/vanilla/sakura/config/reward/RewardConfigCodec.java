package xin.vanilla.sakura.config.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在版本化 JSON 文档与运行时奖励模型之间转换。
 */
public final class RewardConfigCodec {
    private static final Type REWARD_LIST_TYPE = new TypeToken<RewardList>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String encode(RewardConfig config) throws IOException {
        return encodeDocument(toDocument(config));
    }

    public String encodeDocument(RewardConfigDocument document) throws IOException {
        validate(document);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", RewardConfigDocument.CURRENT_SCHEMA_VERSION);
        JsonArray groups = new JsonArray();
        for (RewardGroup group : document.getGroups()) {
            JsonObject object = new JsonObject();
            object.addProperty("rule", group.getRule().name());
            object.addProperty("key", group.getKey());
            if (group.getRule() == ERewardRule.CDK_REWARD) {
                object.addProperty("expirationDate", group.getExpirationDate());
                object.addProperty("redemptionLimit", group.getRedemptionLimit());
            }
            object.add("rewards", group.getRewards().toJsonArray());
            groups.add(object);
        }
        root.add("groups", groups);
        return gson.toJson(root);
    }

    public RewardConfig decode(String json) throws IOException {
        return toRuntimeConfig(decodeDocument(json));
    }

    public RewardConfigDocument decodeDocument(String json) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (version != RewardConfigDocument.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Unsupported reward config schema version: " + version);
            }
            JsonArray array = root.getAsJsonArray("groups");
            if (array == null) {
                throw new IOException("Reward config groups are missing");
            }
            RewardConfigDocument document = new RewardConfigDocument();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                ERewardRule rule = ERewardRule.valueOf(requiredString(object, "rule"));
                RewardList rewards = gson.fromJson(object.get("rewards"), REWARD_LIST_TYPE);
                RewardGroup group = new RewardGroup(rule, requiredString(object, "key"), rewards);
                if (rule == ERewardRule.CDK_REWARD) {
                    group.setExpirationDate(requiredString(object, "expirationDate"));
                    group.setRedemptionLimit(object.has("redemptionLimit")
                            ? object.get("redemptionLimit").getAsInt()
                            : 1);
                }
                document.getGroups().add(group);
            }
            validate(document);
            return document;
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Invalid versioned reward configuration", e);
        }
    }

    public RewardConfigDocument toDocument(RewardConfig config) {
        RewardConfigDocument document = new RewardConfigDocument();
        addGroup(document, ERewardRule.BASE_REWARD, "base", config.getBaseRewards());
        addMap(document, ERewardRule.CONTINUOUS_REWARD, config.getContinuousRewards());
        addMap(document, ERewardRule.CYCLE_REWARD, config.getCycleRewards());
        addMap(document, ERewardRule.YEAR_REWARD, config.getYearRewards());
        addMap(document, ERewardRule.MONTH_REWARD, config.getMonthRewards());
        addMap(document, ERewardRule.WEEK_REWARD, config.getWeekRewards());
        addMap(document, ERewardRule.DATE_TIME_REWARD, config.getDateTimeRewards());
        addMap(document, ERewardRule.CUMULATIVE_REWARD, config.getCumulativeRewards());
        for (RewardGroup group : config.getRandomRewardGroups()) {
            addGroup(document, ERewardRule.RANDOM_REWARD, group.getKey(), group.getRewards());
        }
        for (KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> cdk
                : config.getCdkRewards()) {
            document.getGroups().add(RewardGroup.cdk(
                    cdk.key().key(),
                    cdk.key().value(),
                    cdk.value().value().get(),
                    copy(cdk.value().key())));
        }
        return document;
    }

    public RewardConfig toRuntimeConfig(RewardConfigDocument document) throws IOException {
        validate(document);
        RewardConfig config = new RewardConfig();
        for (RewardGroup group : document.getGroups()) {
            RewardList rewards = copy(group.getRewards());
            switch (group.getRule()) {
                case BASE_REWARD:
                    config.getBaseRewards().addAll(rewards);
                    break;
                case CONTINUOUS_REWARD:
                    putUnique(config.getContinuousRewards(), group, rewards);
                    break;
                case CYCLE_REWARD:
                    putUnique(config.getCycleRewards(), group, rewards);
                    break;
                case YEAR_REWARD:
                    putUnique(config.getYearRewards(), group, rewards);
                    break;
                case MONTH_REWARD:
                    putUnique(config.getMonthRewards(), group, rewards);
                    break;
                case WEEK_REWARD:
                    putUnique(config.getWeekRewards(), group, rewards);
                    break;
                case DATE_TIME_REWARD:
                    putUnique(config.getDateTimeRewards(), group, rewards);
                    break;
                case CUMULATIVE_REWARD:
                    putUnique(config.getCumulativeRewards(), group, rewards);
                    break;
                case RANDOM_REWARD:
                    config.addRandomRewardGroup(group.getKey(), rewards);
                    break;
                case CDK_REWARD:
                    config.addCdkReward(new KeyValue<>(
                            new KeyValue<>(group.getKey(), group.getExpirationDate()),
                            new KeyValue<>(rewards, new AtomicInteger(group.getRedemptionLimit()))));
                    break;
                default:
                    throw new IOException("Unsupported reward rule: " + group.getRule());
            }
        }
        return config;
    }

    public void assertEquivalent(RewardConfigDocument expected, RewardConfig actual) throws IOException {
        String expectedJson = encodeDocument(canonicalOrder(expected));
        String actualJson = encodeDocument(canonicalOrder(toDocument(actual)));
        if (!expectedJson.equals(actualJson)) {
            throw new IOException("Reward configuration changed during migration round trip");
        }
    }

    private void validate(RewardConfigDocument document) throws IOException {
        if (document == null || document.getSchemaVersion() != RewardConfigDocument.CURRENT_SCHEMA_VERSION
                || document.getGroups() == null) {
            throw new IOException("Invalid reward configuration document");
        }
        for (RewardGroup group : document.getGroups()) {
            validate(group);
        }
    }

    private void validate(RewardGroup group) throws IOException {
        if (group == null || group.getRule() == null || group.getKey() == null
                || group.getRewards() == null) {
            throw new IOException("Incomplete reward group");
        }
        String key = group.getKey();
        switch (group.getRule()) {
            case BASE_REWARD:
                if (!"base".equals(key)) {
                    throw new IOException("Base reward key must be base");
                }
                break;
            case CONTINUOUS_REWARD:
            case CYCLE_REWARD:
            case CUMULATIVE_REWARD:
                requireRange(key, 1, Integer.MAX_VALUE, group.getRule());
                break;
            case YEAR_REWARD:
                requireSignedRange(key, 366, group.getRule());
                break;
            case MONTH_REWARD:
                requireSignedRange(key, 31, group.getRule());
                break;
            case WEEK_REWARD:
                requireRange(key, 1, 7, group.getRule());
                break;
            case DATE_TIME_REWARD:
                if (RewardConfig.parseDateRange(key).isEmpty()) {
                    throw new IOException("Invalid date-time reward key: " + key);
                }
                break;
            case RANDOM_REWARD:
                try {
                    BigDecimal probability = new BigDecimal(key);
                    if (probability.compareTo(BigDecimal.ZERO) <= 0
                            || probability.compareTo(BigDecimal.ONE) > 0) {
                        throw new IOException("Random reward probability is out of range: " + key);
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid random reward probability: " + key, e);
                }
                break;
            case CDK_REWARD:
                if (key.isEmpty() || group.getRedemptionLimit() < 1) {
                    throw new IOException("Invalid CDK reward group: " + key);
                }
                break;
            default:
                throw new IOException("Unsupported reward rule: " + group.getRule());
        }
        for (Reward reward : group.getRewards()) {
            if (reward == null || reward.getType() == null || reward.getContent() == null
                    || reward.getProbability() == null) {
                throw new IOException("Incomplete reward in group: " + key);
            }
        }
    }

    private static void requireRange(String key, int min, int max, ERewardRule rule)
            throws IOException {
        try {
            int value = Integer.parseInt(key);
            if (value < min || value > max) {
                throw new IOException("Reward key is out of range for " + rule + ": " + key);
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid reward key for " + rule + ": " + key, e);
        }
    }

    private static void requireSignedRange(String key, int max, ERewardRule rule)
            throws IOException {
        try {
            int value = Integer.parseInt(key);
            if (value == 0 || value < -max || value > max) {
                throw new IOException("Reward key is out of range for " + rule + ": " + key);
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid reward key for " + rule + ": " + key, e);
        }
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            throw new IOException("Missing reward config property: " + name);
        }
        return object.get(name).getAsString();
    }

    private static void addMap(RewardConfigDocument document, ERewardRule rule,
                               Map<String, RewardList> rewards) {
        for (Map.Entry<String, RewardList> entry : rewards.entrySet()) {
            addGroup(document, rule, entry.getKey(), entry.getValue());
        }
    }

    private static void addGroup(RewardConfigDocument document, ERewardRule rule,
                                 String key, RewardList rewards) {
        document.getGroups().add(new RewardGroup(rule, key, copy(rewards)));
    }

    private static void putUnique(Map<String, RewardList> target, RewardGroup group,
                                  RewardList rewards) throws IOException {
        String key = canonicalKey(group.getRule(), group.getKey());
        if (target.put(key, rewards) != null) {
            throw new IOException("Duplicate key is not supported for " + group.getRule()
                    + ": " + key);
        }
    }

    private static String canonicalKey(ERewardRule rule, String key) throws IOException {
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
            throw new IOException("Invalid reward key for " + rule + ": " + key, e);
        }
    }

    private static RewardConfigDocument canonicalOrder(RewardConfigDocument source) {
        RewardConfigDocument result = new RewardConfigDocument();
        List<RewardGroup> groups = new ArrayList<>(source.getGroups());
        groups.sort(Comparator.comparingInt(group -> group.getRule().ordinal()));
        result.setGroups(groups);
        return result;
    }

    private static RewardList copy(List<Reward> rewards) {
        RewardList copy = new RewardList();
        for (Reward reward : rewards) {
            copy.add(reward.clone());
        }
        return copy;
    }
}
