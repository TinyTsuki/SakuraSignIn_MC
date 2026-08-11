package xin.vanilla.sakura.data.migration;

import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Banira 独立玩家摘要持久化边界。
 */
public interface PlayerSummaryStore {
    Optional<PlayerSignInSummary> load(UUID playerUuid) throws IOException;

    /**
     * 保存运行时摘要，由 Banira 的玩家保存生命周期统一落盘。
     */
    void save(UUID playerUuid, PlayerSignInSummary summary) throws IOException;

    /**
     * 迁移专用：强制落盘并回读验证后，才允许删除旧数据。
     */
    void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) throws IOException;
}
