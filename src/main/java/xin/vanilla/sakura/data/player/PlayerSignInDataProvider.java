package xin.vanilla.sakura.data.player;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 玩家数据提供者
 */
public class PlayerSignInDataProvider implements ICapabilityProvider, INBTSerializable<CompoundNBT> {

    // 玩家数据实例
    private IPlayerSignInData playerData;
    private final LazyOptional<IPlayerSignInData> instance = LazyOptional.of(this::getOrCreateCapability);

    /**
     * 获取指定能力的实例
     */
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == PlayerSignInDataCapability.PLAYER_DATA ? instance.cast() : LazyOptional.empty();
    }

    @Nonnull
    IPlayerSignInData getOrCreateCapability() {
        if (playerData == null) {
            this.playerData = new PlayerSignInData();
        }
        return this.playerData;
    }

    /**
     * 序列化玩家数据
     */
    @Override
    public CompoundNBT serializeNBT() {
        return this.getOrCreateCapability().serializeNBT();
    }

    /**
     * 反序列化玩家数据
     */
    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        this.getOrCreateCapability().deserializeNBT(nbt);
    }
}
