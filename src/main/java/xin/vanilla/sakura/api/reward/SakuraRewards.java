package xin.vanilla.sakura.api.reward;

import xin.vanilla.banira.api.permission.BaniraVirtualPermission;
import xin.vanilla.banira.api.permission.BaniraVirtualPermissionRegistry;

import java.util.*;

/**
 * 奖励类型的公共注册入口；冻结后只允许查询。
 */
public final class SakuraRewards {
    private static final Map<RewardTypeId, RewardTypeDefinition<?>> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<RewardTypeId, RewardTypeDefinition<?>> SCRIPTS = new LinkedHashMap<>();
    private static boolean frozen;

    private SakuraRewards() {
    }

    public static synchronized <T> RewardTypeDefinition<T> register(RewardTypeDefinition<T> definition) {
        if (frozen) {
            throw new IllegalStateException("Reward type registry is already frozen");
        }
        RewardTypeId id = definition.getId();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Reward type is already registered: " + id);
        }
        registerVirtualPermission(definition.getAddPermission().getVirtualPermissionKey());
        DEFINITIONS.put(id, definition);
        return definition;
    }

    public static synchronized Optional<RewardTypeDefinition<?>> find(RewardTypeId id) {
        return Optional.ofNullable(DEFINITIONS.containsKey(id) ? DEFINITIONS.get(id) : SCRIPTS.get(id));
    }

    /**
     * Separate server-session types; the frozen mod registry cannot be overwritten.
     */
    public static synchronized void replaceScriptTypes(List<RewardTypeDefinition<?>> definitions) {
        Map<RewardTypeId, RewardTypeDefinition<?>> next = new LinkedHashMap<>();
        for (RewardTypeDefinition<?> definition : definitions) {
            RewardTypeId id = definition.getId();
            if (DEFINITIONS.containsKey(id) || next.put(id, definition) != null) {
                throw new IllegalArgumentException("Duplicate reward type: " + id);
            }
        }
        next.values().forEach(definition -> registerVirtualPermission(definition.getAddPermission().getVirtualPermissionKey()));
        SCRIPTS.clear();
        SCRIPTS.putAll(next);
    }

    public static synchronized RewardTypeDefinition<?> require(RewardTypeId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown reward type: " + id));
    }

    public static synchronized List<RewardTypeDefinition<?>> all() {
        List<RewardTypeDefinition<?>> result = new ArrayList<>(DEFINITIONS.values());
        result.addAll(SCRIPTS.values());
        result.sort((first, second) -> first.getId().compareTo(second.getId()));
        return Collections.unmodifiableList(result);
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    static synchronized void clearForTests() {
        DEFINITIONS.clear();
        SCRIPTS.clear();
        frozen = false;
    }

    private static void registerVirtualPermission(String key) {
        if (BaniraVirtualPermissionRegistry.find(key).isPresent()) {
            return;
        }
        int separator = key.indexOf(':');
        BaniraVirtualPermissionRegistry.register(new RegisteredRewardPermission(
                key.substring(0, separator), key.substring(separator + 1)));
    }

    private static final class RegisteredRewardPermission implements BaniraVirtualPermission {
        private final String modId;
        private final String id;

        private RegisteredRewardPermission(String modId, String id) {
            this.modId = modId;
            this.id = id;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean op() {
            return true;
        }

        @Override
        public int sort() {
            return Integer.MAX_VALUE;
        }
    }
}
