package xin.vanilla.sakura.platform;

import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;

import java.io.IOException;
import java.util.UUID;

/**
 * 玩家对象和加载器事件由当前版本适配，业务层只依赖此契约。
 */
public interface SakuraPlayerDataService {
    IPlayerSignInData get(Object player);

    void setClient(UUID playerUuid, IPlayerSignInData data);

    LegacyMigrationResult migrateAndLoad(Object serverPlayer) throws IOException;

    void save(Object serverPlayer);

    void saveAndSync(Object serverPlayer);

    void sync(Object serverPlayer);

    void removeServer(UUID playerUuid);

    void clearClient();

    void saveAllAndClear();
}
