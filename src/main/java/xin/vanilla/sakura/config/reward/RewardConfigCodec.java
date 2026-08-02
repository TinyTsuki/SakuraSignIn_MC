package xin.vanilla.sakura.config.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardJsonCodec;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresetValidator;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPoolValidator;
import xin.vanilla.sakura.data.lottery.LotteryPools;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在版本化 JSON 文档与运行时奖励模型之间转换。
 */
public final class RewardConfigCodec {
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
            JsonArray rewards = new JsonArray();
            group.getRewards().forEach(reward -> rewards.add(RewardJsonCodec.encode(reward)));
            object.add("rewards", rewards);
            groups.add(object);
        }
        root.add("groups", groups);
        JsonArray presets = new JsonArray();
        for (PersonalDatePreset preset : document.getPersonalDatePresets()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", preset.getId());
            object.addProperty("displayName", preset.getDisplayName());
            object.addProperty("recurrence", preset.getRecurrence().name());
            JsonArray calendarIds = new JsonArray();
            preset.getCalendarIds().forEach(calendarIds::add);
            object.add("calendarIds", calendarIds);
            object.addProperty("maxDateSlots", preset.getMaxDateSlots());
            object.addProperty("deliveryMode", preset.getDeliveryMode().name());
            object.addProperty("validBeforeDays", preset.getValidBeforeDays());
            object.addProperty("validAfterDays", preset.getValidAfterDays());
            JsonArray rewards = new JsonArray();
            preset.getRewards().forEach(reward -> rewards.add(RewardJsonCodec.encode(reward)));
            object.add("rewards", rewards);
            presets.add(object);
        }
        root.add("personalDatePresets", presets);
        JsonArray lotteryPools = new JsonArray();
        for (LotteryPool pool : document.getLotteryPools()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", pool.getId());
            object.addProperty("displayName", pool.getDisplayName());
            object.addProperty("limitPolicy", pool.getLimitPolicy().name());
            object.addProperty("maxDraws", pool.getMaxDraws());
            object.addProperty("cooldownSeconds", pool.getCooldownSeconds());
            object.addProperty("previewMode", pool.getPreviewMode().name());
            JsonArray rewards = new JsonArray();
            pool.getRewards().forEach(reward -> rewards.add(RewardJsonCodec.encode(reward)));
            object.add("rewards", rewards);
            lotteryPools.add(object);
        }
        root.add("lotteryPools", lotteryPools);
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
                JsonArray rewardArray = object.getAsJsonArray("rewards");
                if (rewardArray == null) {
                    throw new IOException("Reward group rewards are missing");
                }
                RewardList rewards = new RewardList();
                for (JsonElement reward : rewardArray) {
                    rewards.add(RewardJsonCodec.decode(reward));
                }
                RewardGroup group = new RewardGroup(rule, requiredString(object, "key"), rewards);
                if (rule == ERewardRule.CDK_REWARD) {
                    group.setExpirationDate(requiredString(object, "expirationDate"));
                    group.setRedemptionLimit(object.has("redemptionLimit")
                            ? object.get("redemptionLimit").getAsInt()
                            : 1);
                }
                document.getGroups().add(group);
            }
            JsonArray presetArray = root.getAsJsonArray("personalDatePresets");
            if (presetArray != null) {
                for (JsonElement element : presetArray) {
                    document.getPersonalDatePresets().add(decodePersonalDatePreset(
                            element.getAsJsonObject()));
                }
            }
            JsonArray lotteryArray = root.getAsJsonArray("lotteryPools");
            if (lotteryArray != null) {
                for (JsonElement element : lotteryArray) {
                    document.getLotteryPools().add(decodeLotteryPool(element.getAsJsonObject()));
                }
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
        config.getPersonalDatePresets().forEach(preset ->
                document.getPersonalDatePresets().add(copy(preset)));
        document.setLotteryPools(LotteryPools.copy(config.getLotteryPools()));
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
        document.getPersonalDatePresets().forEach(preset ->
                config.getPersonalDatePresets().add(copy(preset)));
        config.setLotteryPools(LotteryPools.copy(document.getLotteryPools()));
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
                || document.getGroups() == null || document.getPersonalDatePresets() == null
                || document.getLotteryPools() == null) {
            throw new IOException("Invalid reward configuration document");
        }
        for (RewardGroup group : document.getGroups()) {
            validate(group);
        }
        Set<String> presetIds = new HashSet<>();
        if (document.getPersonalDatePresets().size() > 128) {
            throw new IOException("Too many personal date presets");
        }
        for (PersonalDatePreset preset : document.getPersonalDatePresets()) {
            List<String> errors = PersonalDatePresetValidator.validate(preset);
            if (!errors.isEmpty()) {
                throw new IOException("Invalid personal date preset "
                        + (preset == null ? "" : preset.getId()) + ": " + errors);
            }
            if (!presetIds.add(preset.getId())) {
                throw new IOException("Duplicate personal date preset id: " + preset.getId());
            }
            if (preset.getRewards().size() > 256) {
                throw new IOException("Too many rewards in personal date preset: "
                        + preset.getId());
            }
            for (Reward reward : preset.getRewards()) {
                validateReward(reward, preset.getId());
            }
        }
        Set<String> lotteryIds = new HashSet<>();
        if (document.getLotteryPools().size() > 128) {
            throw new IOException("Too many lottery pools");
        }
        for (LotteryPool pool : document.getLotteryPools()) {
            List<String> errors = LotteryPoolValidator.validate(pool);
            if (!errors.isEmpty() || !lotteryIds.add(pool.getId())) {
                throw new IOException("Invalid lottery pool "
                        + (pool == null ? "" : pool.getId()) + ": " + errors);
            }
            for (Reward reward : pool.getRewards()) {
                validateReward(reward, pool.getId());
            }
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
            validateReward(reward, key);
        }
    }

    private static void validateReward(Reward reward, String owner) throws IOException {
        if (reward == null || reward.getTypeId() == null || reward.getContent() == null
                || reward.getProbability() == null) {
            throw new IOException("Incomplete reward in group: " + owner);
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
        result.setPersonalDatePresets(new ArrayList<>(source.getPersonalDatePresets()));
        result.setLotteryPools(LotteryPools.copy(source.getLotteryPools()));
        return result;
    }

    private static PersonalDatePreset decodePersonalDatePreset(JsonObject object)
            throws IOException {
        JsonArray rewardArray = object.getAsJsonArray("rewards");
        if (rewardArray == null) {
            throw new IOException("Personal date preset rewards are missing");
        }
        RewardList rewards = new RewardList();
        for (JsonElement reward : rewardArray) {
            rewards.add(RewardJsonCodec.decode(reward));
        }
        List<String> calendarIds = new ArrayList<>();
        JsonArray calendarArray = object.getAsJsonArray("calendarIds");
        if (calendarArray != null) {
            calendarArray.forEach(element -> calendarIds.add(element.getAsString()));
        }
        return new PersonalDatePreset(
                requiredString(object, "id"),
                requiredString(object, "displayName"),
                PersonalDateRecurrence.valueOf(requiredString(object, "recurrence")),
                calendarIds,
                object.get("maxDateSlots").getAsInt(),
                PersonalDateDeliveryMode.valueOf(requiredString(object, "deliveryMode")),
                object.get("validBeforeDays").getAsInt(),
                object.get("validAfterDays").getAsInt(),
                rewards
        );
    }

    private static LotteryPool decodeLotteryPool(JsonObject object) throws IOException {
        JsonArray rewardArray = object.getAsJsonArray("rewards");
        if (rewardArray == null) {
            throw new IOException("Lottery pool rewards are missing");
        }
        RewardList rewards = new RewardList();
        for (JsonElement reward : rewardArray) {
            rewards.add(RewardJsonCodec.decode(reward));
        }
        LotteryPreviewMode previewMode;
        if (object.has("previewMode")) {
            previewMode = LotteryPreviewMode.valueOf(requiredString(object, "previewMode"));
        } else {
            // 奖励配置属于持久化数据，旧布尔值在读取时迁移为新的四态预览策略。
            previewMode = !object.has("showRewards") || object.get("showRewards").getAsBoolean()
                    ? LotteryPreviewMode.ALL
                    : LotteryPreviewMode.NONE;
        }
        return new LotteryPool(
                requiredString(object, "id"),
                requiredString(object, "displayName"),
                LotteryLimitPolicy.valueOf(requiredString(object, "limitPolicy")),
                object.has("maxDraws") ? object.get("maxDraws").getAsInt() : 1,
                object.has("cooldownSeconds") ? object.get("cooldownSeconds").getAsInt() : 0,
                previewMode,
                rewards
        );
    }

    private static PersonalDatePreset copy(PersonalDatePreset preset) {
        return new PersonalDatePreset(
                preset.getId(), preset.getDisplayName(), preset.getRecurrence(),
                new ArrayList<>(preset.getCalendarIds()), preset.getMaxDateSlots(), preset.getDeliveryMode(),
                preset.getValidBeforeDays(), preset.getValidAfterDays(), copy(preset.getRewards())
        );
    }

    private static RewardList copy(List<Reward> rewards) {
        RewardList copy = new RewardList();
        for (Reward reward : rewards) {
            copy.add(reward.clone());
        }
        return copy;
    }
}
