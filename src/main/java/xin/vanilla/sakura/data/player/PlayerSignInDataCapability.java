package xin.vanilla.sakura.data.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.util.LazyOptional;
import xin.vanilla.sakura.network.packet.PlayerDataSyncToBoth;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * 玩家数据能力
 */
public class PlayerSignInDataCapability {
    // 定义 Capability 实例
    @CapabilityInject(IPlayerSignInData.class)
    public static Capability<IPlayerSignInData> PLAYER_DATA;

    public static void register() {
        CapabilityManager.INSTANCE.register(IPlayerSignInData.class, new PlayerSignInDataStorage(), PlayerSignInData::new);
    }

    /**
     * 获取玩家数据
     */
    public static IPlayerSignInData getData(PlayerEntity player) {
        return player.getCapability(PLAYER_DATA).orElseThrow(() -> new IllegalArgumentException("Player data capability is missing."));
    }

    public static LazyOptional<IPlayerSignInData> getDataOptional(ServerPlayerEntity player) {
        return player.getCapability(PLAYER_DATA);
    }

    /**
     * 设置玩家数据
     */
    public static void setData(PlayerEntity player, IPlayerSignInData data) {
        player.getCapability(PLAYER_DATA).ifPresent(capability -> capability.copyFrom(data));
    }

    /**
     * 同步玩家数据到客户端
     */
    public static void syncPlayerData(ServerPlayerEntity player) {
        // 创建自定义包并发送到客户端
        PlayerDataSyncToBoth packet = new PlayerDataSyncToBoth(player.getUUID(), PlayerSignInDataCapability.getData(player));
        for (PlayerDataSyncToBoth syncPacket : packet.split()) {
            SakuraUtils.sendPacketToPlayer(syncPacket, player);
        }
    }
}
