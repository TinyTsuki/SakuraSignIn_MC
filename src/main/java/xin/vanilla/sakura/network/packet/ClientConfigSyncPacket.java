package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInDataCapability;

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

    public ClientConfigSyncPacket(PacketBuffer buf) {
        this.autoRewarded = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeBoolean(this.autoRewarded);
    }

    public static void handle(ClientConfigSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                signInData.setAutoRewarded(packet.autoRewarded);
                signInData.save(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
