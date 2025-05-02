package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.data.PlayerSignInData;

@Getter
public class ClientConfigSyncPacket implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "client_config_sync");

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

    public void write(@NonNull FriendlyByteBuf buf) {
        buf.writeBoolean(this.autoRewarded);
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(ClientConfigSyncPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            ctx.workHandler().execute(() -> {
                if (ctx.player().isPresent()) {
                    ServerPlayer player = (ServerPlayer) ctx.player().get();
                    PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                    signInData.setAutoRewarded(packet.autoRewarded);
                }
            });
        }
    }
}
