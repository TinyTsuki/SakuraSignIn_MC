package xin.vanilla.sakura.api.reward.client;

import lombok.Value;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端展示与编辑扩展注册表，与服务端奖励定义分别冻结。
 */
public final class SakuraRewardClient {
    private static final Map<RewardTypeId, Registration<?>> EXTENSIONS = new LinkedHashMap<>();
    private static boolean frozen;

    private SakuraRewardClient() {
    }

    public static synchronized <T> Registration<T> register(
            RewardTypeId typeId, RewardClientExtension<T> extension) {
        if (frozen) {
            throw new IllegalStateException("Reward client registry is already frozen");
        }
        if (EXTENSIONS.containsKey(typeId)) {
            throw new IllegalStateException("Reward client extension is already registered: " + typeId);
        }
        Registration<T> registration = new Registration<>(typeId, extension);
        EXTENSIONS.put(typeId, registration);
        return registration;
    }

    public static synchronized Optional<Registration<?>> find(RewardTypeId typeId) {
        return Optional.ofNullable(EXTENSIONS.get(typeId));
    }

    public static synchronized Registration<?> require(RewardTypeId typeId) {
        return find(typeId).orElseThrow(() ->
                new IllegalArgumentException("Missing reward client extension: " + typeId));
    }

    public static synchronized List<Registration<?>> all() {
        List<Registration<?>> result = new ArrayList<>(EXTENSIONS.values());
        result.sort((first, second) -> {
            int bySort = Integer.compare(first.getExtension().getSortOrder(),
                    second.getExtension().getSortOrder());
            return bySort != 0 ? bySort : first.getTypeId().compareTo(second.getTypeId());
        });
        return Collections.unmodifiableList(result);
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /** 客户端名称和图标必须由同一展示扩展解析。 */
    public static Component displayName(Reward reward, String languageCode,
                                        boolean withAmount) {
        Registration<?> registration = reward == null ? null
                : find(reward.getTypeId()).orElse(null);
        if (registration == null) {
            return reward == null ? xin.vanilla.sakura.SakuraComponent.get().literal("")
                    : RewardOperations.describe(languageCode, reward, withAmount);
        }
        try {
            return displayNameResolved(registration, reward, languageCode, withAmount);
        } catch (RuntimeException exception) {
            return RewardOperations.describe(languageCode, reward, withAmount);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Component displayNameResolved(Registration registration, Reward reward,
                                                 String languageCode, boolean withAmount) {
        Object value = RewardOperations.decode(reward);
        RewardDisplayContext context = new RewardDisplayContext() {
            @Override public Reward reward() { return reward; }
            @Override public String languageCode() { return languageCode; }
            @Override public boolean withAmount() { return withAmount; }
        };
        return registration.getExtension().getPresentation().displayName(context, value);
    }

    static synchronized void clearForTests() {
        EXTENSIONS.clear();
        frozen = false;
    }

    @Value
    public static class Registration<T> {
        RewardTypeId typeId;
        RewardClientExtension<T> extension;
    }
}
