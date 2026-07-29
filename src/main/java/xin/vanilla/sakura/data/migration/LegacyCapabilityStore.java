package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundNBT;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 旧加载器玩家数据节点的版本适配边界。
 */
public interface LegacyCapabilityStore {
    Optional<CompoundNBT> read(UUID playerUuid) throws IOException;

    String backupAndVerify(UUID playerUuid, CompoundNBT capability) throws IOException;

    boolean backupMatches(String relativePath, CompoundNBT capability) throws IOException;

    void removeAndVerify(UUID playerUuid, CompoundNBT expectedCapability) throws IOException;
}
