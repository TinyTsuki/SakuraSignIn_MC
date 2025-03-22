package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInDataCapability;

@Getter
public class ClientConfigSyncPacket {
    /**
     * 自动领取奖励
     */
    private final boolean autoRewarded;

    public ClientConfigSyncPacket() {
        this.autoRewarded = ClientConfig.AUTO_REWARDED.get();
    }

    public ClientConfigSyncPacket(FriendlyByteBuf buf) {
        this.autoRewarded = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.autoRewarded);
    }

    public static void handle(ClientConfigSyncPacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                signInData.setAutoRewarded(packet.autoRewarded);
                signInData.save(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
