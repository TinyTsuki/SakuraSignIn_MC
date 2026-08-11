package xin.vanilla.sakura.data.migration;

import xin.vanilla.sakura.data.migration.LegacyPlayerData;

import java.io.IOException;
import java.util.UUID;

/**
 * 按月签到详情持久化边界。
 */
public interface SignInHistoryStore {
    /**
     * 保存运行时月度详情，不执行迁移级回读验证。
     */
    void save(UUID playerUuid, LegacyPlayerData playerData) throws IOException;

    /**
     * 迁移专用：保存全部月份并逐文件回读验证。
     */
    void saveAndVerify(UUID playerUuid, LegacyPlayerData playerData) throws IOException;
}
