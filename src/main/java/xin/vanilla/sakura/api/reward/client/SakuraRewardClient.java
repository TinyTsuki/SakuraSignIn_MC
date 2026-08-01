package xin.vanilla.sakura.api.reward.client;

import lombok.Value;
import xin.vanilla.sakura.api.reward.RewardTypeId;

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
