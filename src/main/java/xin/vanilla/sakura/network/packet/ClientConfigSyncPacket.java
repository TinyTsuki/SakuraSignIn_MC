package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;

@Getter
public class ClientConfigSyncPacket implements INetworkPacket {
    /**
     * 自动领取奖励
     */
    private final boolean autoRewarded;

    public ClientConfigSyncPacket() {
        this.autoRewarded = ClientConfig.get().display().autoRewarded();
    }

    public ClientConfigSyncPacket(BaniraPacketBuffer buf) {
        this.autoRewarded = buf.readBoolean();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeBoolean(this.autoRewarded);
    }

    public static void handle(ClientConfigSyncPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.senderAs(ServerPlayerEntity.class);
            if (player != null) {
                IPlayerSignInData signInData = SakuraPlayerData.get(player);
                signInData.setAutoRewarded(packet.autoRewarded);
                SakuraPlayerData.saveAndSync(player);
            }
        });
        ctx.markHandled();
    }
}
