package xin.vanilla.sakura.api;

import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.platform.SakuraPlayerDataService;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Sakura 内部业务统一使用的玩家数据入口。
 */
public final class SakuraPlayerData {
    private static volatile SakuraPlayerDataService service;

    private SakuraPlayerData() {
    }

    public static void install(SakuraPlayerDataService playerDataService) {
        service = Objects.requireNonNull(playerDataService, "playerDataService");
    }

    public static IPlayerSignInData get(Object player) {
        return service().get(player);
    }

    public static void setClient(UUID playerUuid, IPlayerSignInData data) {
        service().setClient(playerUuid, data);
    }

    public static LegacyMigrationResult migrateAndLoad(Object serverPlayer) throws IOException {
        return service().migrateAndLoad(serverPlayer);
    }

    public static void save(Object serverPlayer) {
        service().save(serverPlayer);
    }

    public static void saveAndSync(Object serverPlayer) {
        service().saveAndSync(serverPlayer);
    }

    public static void sync(Object serverPlayer) {
        service().sync(serverPlayer);
    }

    public static void removeServer(UUID playerUuid) {
        service().removeServer(playerUuid);
    }

    public static void clearClient() {
        service().clearClient();
    }

    public static void saveAllAndClear() {
        service().saveAllAndClear();
    }

    private static SakuraPlayerDataService service() {
        SakuraPlayerDataService current = service;
        if (current == null) {
            throw new IllegalStateException("Sakura player data service is not installed");
        }
        return current;
    }
}
