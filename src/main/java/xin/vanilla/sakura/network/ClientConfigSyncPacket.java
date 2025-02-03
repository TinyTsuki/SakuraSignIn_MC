package xin.vanilla.sakura.network;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.sakura.capability.IPlayerSignInData;
import xin.vanilla.sakura.capability.PlayerSignInDataCapability;
import xin.vanilla.sakura.config.ClientConfig;

import java.util.function.Supplier;

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

    public static void handle(ClientConfigSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                signInData.setAutoRewarded(packet.autoRewarded);
                signInData.save(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
