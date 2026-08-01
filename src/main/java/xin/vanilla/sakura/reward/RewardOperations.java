package xin.vanilla.sakura.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.*;

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
                || first.getProbability().compareTo(second.getProbability()) != 0) {
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
