package xin.vanilla.sakura.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;

/**
 * 玩家签到数据能力
 */
public class PlayerSignInDataCapability {
    // 定义 Capability 实例
    public static Capability<IPlayerSignInData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {
    });

    /**
     * 获取玩家签到数据
     *
     * @param player 玩家实体
     * @return 玩家的签到数据
     */
    public static IPlayerSignInData getData(Player player) {
        return player.getCapability(PLAYER_DATA).orElseThrow(() -> new IllegalArgumentException("Player data capability is missing."));
    }

    public static LazyOptional<IPlayerSignInData> getDataOptional(ServerPlayer player) {
        return player.getCapability(PLAYER_DATA);
    }

    /**
     * 设置玩家签到数据
     *
     * @param player 玩家实体
     * @param data   玩家签到数据
     */
    public static void setData(Player player, IPlayerSignInData data) {
        LazyOptional<IPlayerSignInData> optional = player.getCapability(PLAYER_DATA);
        if (optional.isPresent()) {
            optional.ifPresent(capability -> capability.copyFrom(data));
        } else {
            throw new IllegalArgumentException("Player data capability is missing.");
        }
    }

    /**
     * 同步玩家签到数据到客户端
     */
    public static void syncPlayerData(ServerPlayer player) {
        // 创建自定义包并发送到客户端
        PlayerDataSyncPacket packet = new PlayerDataSyncPacket(player.getUUID(), PlayerSignInDataCapability.getData(player));
        for (PlayerDataSyncPacket syncPacket : packet.split()) {
            ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), syncPacket);
        }
    }
}
