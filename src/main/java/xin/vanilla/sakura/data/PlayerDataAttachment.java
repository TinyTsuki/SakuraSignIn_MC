package xin.vanilla.sakura.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;

import java.util.function.Supplier;

public class PlayerDataAttachment {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SakuraSignIn.MODID);

    public static final Supplier<AttachmentType<PlayerSignInData>> PLAYER_DATA = ATTACHMENT_TYPES.register(
            "player_teleport_data", () -> AttachmentType.serializable(PlayerSignInData::new).build()
    );


    /**
     * 同步玩家传送数据到客户端
     */
    public static void syncPlayerData(ServerPlayer player) {
        // 创建自定义包并发送到客户端
        PlayerDataSyncPacket packet = new PlayerDataSyncPacket(player.getUUID(), player.getData(PLAYER_DATA));
        for (PlayerDataSyncPacket syncPacket : packet.split()) {
            player.connection.send(syncPacket);
        }
    }

    public static PlayerSignInData getData(Player player) {
        return player.getData(PLAYER_DATA);
    }
}
