package xin.vanilla.sakura.data.migration;

import xin.vanilla.sakura.domain.player.LegacyPlayerData;

import java.io.IOException;
import java.util.UUID;

/**
 * 按月签到详情持久化边界。
 */
public interface SignInHistoryStore {
    void saveAndVerify(UUID playerUuid, LegacyPlayerData playerData) throws IOException;
}
