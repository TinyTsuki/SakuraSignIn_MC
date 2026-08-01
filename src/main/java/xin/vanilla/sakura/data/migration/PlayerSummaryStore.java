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

    void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) throws IOException;
}
