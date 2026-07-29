package xin.vanilla.sakura.domain.player;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundNBT;

import java.util.List;
import java.util.Map;

/**
 * 旧 Capability 的一次性解析结果。
 */
@Getter
@RequiredArgsConstructor
public class LegacyPlayerData {
    private final PlayerSignInSummary summary;
    private final Map<String, List<CompoundNBT>> recordsByMonth;
}
