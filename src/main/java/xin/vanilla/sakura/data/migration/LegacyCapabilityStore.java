package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 旧加载器玩家数据节点的版本适配边界。
 */
public interface LegacyCapabilityStore {
    Optional<CompoundTag> read(UUID playerUuid) throws IOException;

    String backupAndVerify(UUID playerUuid, CompoundTag capability) throws IOException;

    boolean backupMatches(String relativePath, CompoundTag capability) throws IOException;

    void removeAndVerify(UUID playerUuid, CompoundTag expectedCapability) throws IOException;
}
