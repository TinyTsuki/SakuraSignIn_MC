package xin.vanilla.sakura.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * 注册表的类型擦除边界，业务调用方不需要自行强制转换。
 */
public final class RewardOperations {
    private static final Logger LOGGER = LogManager.getLogger();

    private RewardOperations() {
    }

    public static <T> JsonObject encode(RewardTypeId typeId, T value) {
        try {
            return definition(typeId).getCodec().encode(value);
        } catch (RewardDataException exception) {
            throw new JsonParseException("Unable to encode reward type " + typeId, exception);
        }
    }

    public static <T> T decode(Reward reward) {
        try {
            return RewardOperations.<T>definition(reward.getTypeId()).getCodec()
                    .decode(reward.getContent());
        } catch (RewardDataException exception) {
            throw new JsonParseException("Unable to decode reward type " + reward.getTypeId(), exception);
        }
    }

    public static Component describe(String languageCode, Reward reward, boolean withAmount) {
        RewardTypeDefinition<?> raw = SakuraRewards.find(reward.getTypeId()).orElse(null);
        if (raw == null) {
            return SakuraComponent.get().literal(reward.getTypeId().toString());
        }
        try {
            return describeResolved(raw, languageCode, reward.getContent(), withAmount);
        } catch (RewardDataException | RuntimeException exception) {
            LOGGER.warn("Unable to describe reward type {}", reward.getTypeId(), exception);
            return SakuraComponent.get().literal(reward.getTypeId().toString());
        }
    }

    public static RewardGrantResult grant(RewardGrantContext context, Reward reward) {
        RewardTypeDefinition<?> raw = SakuraRewards.find(reward.getTypeId()).orElse(null);
        if (raw == null) {
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE,
                    reward.getTypeId().toString());
        }
        try {
            return grantResolved(raw, context, reward.getContent());
        } catch (RewardDataException exception) {
            return RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Reward executor failed for type {}", reward.getTypeId(), exception);
            return RewardGrantResult.of(RewardGrantStatus.FAILED, exception.getClass().getSimpleName());
        }
    }

    /**
     * 每个奖励独立执行，单个扩展失败不会阻断同批次的其他奖励。
     */
    public static List<RewardGrantResult> grantAll(RewardGrantContext context,
                                                    Iterable<Reward> rewards) {
        List<RewardGrantResult> results = new ArrayList<>();
        if (rewards == null) {
            return results;
        }
        for (Reward reward : rewards) {
            results.add(reward == null
                    ? RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT, "null_reward")
                    : grant(context, reward));
        }
        return results;
    }

    public static boolean semanticallyMatches(Reward first, Reward second) {
        if (first == null || second == null || first.getTypeId() == null
                || !first.getTypeId().equals(second.getTypeId())) {
            return false;
        }
        RewardTypeDefinition<?> raw = SakuraRewards.find(first.getTypeId()).orElse(null);
        if (raw == null || raw.getMerger() == null) {
            return first.getContent() == null
                    ? second.getContent() == null
                    : first.getContent().equals(second.getContent());
        }
        try {
            return semanticKey(raw, first.getContent()).equals(semanticKey(raw, second.getContent()));
        } catch (RewardDataException | RuntimeException exception) {
            return first.getContent() == null
                    ? second.getContent() == null
                    : first.getContent().equals(second.getContent());
        }
    }

    public static Optional<Reward> merge(Reward first, Reward second) {
        if (!semanticallyMatches(first, second)
                || first.getProbability().compareTo(second.getProbability()) != 0
                || first.isRewarded() != second.isRewarded()
                || first.isDisabled() != second.isDisabled()) {
            return Optional.empty();
        }
        RewardTypeDefinition<?> raw = SakuraRewards.find(first.getTypeId()).orElse(null);
        if (raw == null || raw.getMerger() == null) {
            return Optional.empty();
        }
        try {
            return mergeResolved(raw, first, second);
        } catch (RewardDataException | RuntimeException exception) {
            LOGGER.warn("Unable to merge reward type {}", first.getTypeId(), exception);
            return Optional.empty();
        }
    }

    static Optional<RewardList> mergePreparedBuiltIns(RewardList rewards) {
        if (rewards.size() < 2) return Optional.empty();
        Map<RewardTypeId, RewardTypeDefinition<?>> definitions = new LinkedHashMap<>();
        // An extension can mutate other rewards while decoding; preserve the entire legacy fold.
        for (Reward reward : rewards) {
            if (reward == null || reward.getTypeId() == null) return Optional.empty();
            RewardTypeDefinition<?> definition = definitions.get(reward.getTypeId());
            if (definition == null) {
                definition = SakuraRewards.find(reward.getTypeId()).orElse(null);
                if (!BuiltInRewardTypes.supportsMergePreparation(definition)) return Optional.empty();
                definitions.put(reward.getTypeId(), definition);
            }
        }
        List<PreparedReward> inputs = new ArrayList<>(rewards.size());
        try {
            for (Reward reward : rewards) inputs.add(prepare(reward, definitions.get(reward.getTypeId())));
        } catch (RewardDataException | RuntimeException exception) {
            return Optional.empty();
        }
        RewardList output = new RewardList();
        List<PreparedReward> slots = new ArrayList<>();
        Map<MergeGroup, List<Integer>> groups = new LinkedHashMap<>();
        for (PreparedReward candidate : inputs) {
            List<Integer> indices = groups.get(candidate.group);
            boolean merged = false;
            if (indices != null) {
                for (int index : indices) {
                    Optional<PreparedReward> result = slots.get(index).merge(candidate);
                    if (result.isPresent()) {
                        PreparedReward value = result.get();
                        slots.set(index, value); output.set(index, value.reward);
                        if (!candidate.group.equals(value.group)) {
                            indices.remove(Integer.valueOf(index));
                            addIndex(groups, value.group, index);
                        }
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                addIndex(groups, candidate.group, output.size());
                output.add(candidate.reward); slots.add(candidate);
            }
        }
        return Optional.of(output);
    }

    private static void addIndex(Map<MergeGroup, List<Integer>> groups, MergeGroup group, int index) {
        List<Integer> indices = groups.computeIfAbsent(group, ignored -> new ArrayList<>());
        int position = java.util.Collections.binarySearch(indices, index);
        indices.add(position < 0 ? -position - 1 : position, index);
    }

    @SuppressWarnings("unchecked")
    private static PreparedReward prepare(Reward reward, RewardTypeDefinition<?> raw) throws RewardDataException {
        RewardTypeDefinition<Object> definition = (RewardTypeDefinition<Object>) raw;
        Object value = definition.getCodec().decode(reward.getContent());
        return new PreparedReward(reward, definition, value);
    }

    private static final class PreparedReward {
        private final Reward reward;
        private final RewardTypeDefinition<Object> definition;
        private final Object value;
        private final MergeGroup group;

        private PreparedReward(Reward reward, RewardTypeDefinition<Object> definition, Object value) {
            this.reward = reward; this.definition = definition; this.value = value;
            this.group = new MergeGroup(reward, definition.getMerger().key(value));
        }

        private Optional<PreparedReward> merge(PreparedReward second) {
            try {
                Optional<Object> merged = definition.getMerger().merge(value, second.value);
                if (!merged.isPresent()) return Optional.empty();
                Reward result = new Reward(definition.getCodec().encode(merged.get()), reward.getTypeId(), reward.getProbability());
                result.setRewarded(reward.isRewarded()).setDisabled(reward.isDisabled());
                return Optional.of(prepare(result, definition));
            } catch (RewardDataException | RuntimeException exception) {
                LOGGER.warn("Unable to merge reward type {}", reward.getTypeId(), exception);
                return Optional.empty();
            }
        }
    }

    private static final class MergeGroup {
        private final RewardTypeId type;
        private final RewardMergeKey key;
        private final BigDecimal probability;
        private final boolean rewarded;
        private final boolean disabled;

        private MergeGroup(Reward reward, RewardMergeKey key) {
            this.type = reward.getTypeId(); this.key = key;
            this.probability = reward.getProbability().stripTrailingZeros();
            this.rewarded = reward.isRewarded(); this.disabled = reward.isDisabled();
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MergeGroup)) return false;
            MergeGroup value = (MergeGroup) other;
            return rewarded == value.rewarded && disabled == value.disabled
                    && type.equals(value.type) && Objects.equals(key, value.key) && probability.equals(value.probability);
        }
        @Override public int hashCode() { return Objects.hash(type, key, probability, rewarded, disabled); }
    }

    @SuppressWarnings("unchecked")
    private static <T> RewardTypeDefinition<T> definition(RewardTypeId typeId) {
        return (RewardTypeDefinition<T>) SakuraRewards.require(typeId);
    }

    private static <T> Component describeResolved(RewardTypeDefinition<T> definition,
                                                  String languageCode, JsonObject content,
                                                  boolean withAmount) throws RewardDataException {
        T value = definition.getCodec().decode(content);
        return definition.getDescriber().describe(languageCode, value, withAmount);
    }

    private static <T> RewardGrantResult grantResolved(RewardTypeDefinition<T> definition,
                                                       RewardGrantContext context,
                                                       JsonObject content) throws RewardDataException {
        T value = definition.getCodec().decode(content);
        List<RewardViolation> violations = definition.getValidator().validate(value);
        if (violations != null && !violations.isEmpty()) {
            RewardViolation first = violations.get(0);
            return RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT,
                    first.getField() + ":" + first.getCode());
        }
        RewardGrantResult result = definition.getExecutor().grant(context, value);
        return result == null
                ? RewardGrantResult.of(RewardGrantStatus.FAILED, "null_result")
                : result;
    }

    private static <T> RewardMergeKey semanticKey(RewardTypeDefinition<T> definition,
                                                  JsonObject content) throws RewardDataException {
        return definition.getMerger().key(definition.getCodec().decode(content));
    }

    private static <T> Optional<Reward> mergeResolved(RewardTypeDefinition<T> definition,
                                                      Reward first, Reward second)
            throws RewardDataException {
        T firstValue = definition.getCodec().decode(first.getContent());
        T secondValue = definition.getCodec().decode(second.getContent());
        Optional<T> merged = definition.getMerger().merge(firstValue, secondValue);
        if (!merged.isPresent()) {
            return Optional.empty();
        }
        Reward result = new Reward(definition.getCodec().encode(merged.get()),
                first.getTypeId(), first.getProbability());
        result.setRewarded(first.isRewarded()).setDisabled(first.isDisabled());
        return Optional.of(result);
    }
}
