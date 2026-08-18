package xin.vanilla.sakura.data.migration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.util.List;
import java.util.Map;

/**
 * 旧 Capability 的一次性解析结果。
 */
@Getter
@RequiredArgsConstructor
public class LegacyPlayerData {
    private final PlayerSignInSummary summary;
    private final Map<String, List<CompoundTag>> recordsByMonth;
}
