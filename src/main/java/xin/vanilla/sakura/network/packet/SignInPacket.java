package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.rewards.RewardManager;

@Getter
public class SignInPacket implements CustomPacketPayload {
    public final static Type<SignInPacket> TYPE = new Type<>(new ResourceLocation(SakuraSignIn.MODID, "sign_in"));
    public final static StreamCodec<ByteBuf, SignInPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull SignInPacket decode(@NotNull ByteBuf byteBuf) {
            return new SignInPacket((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull SignInPacket packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    private final String signInTime;
    private final boolean autoRewarded;
    private final ESignInType signInType;

    public SignInPacket(String signInTime, boolean autoRewarded, ESignInType signInType) {
        this.signInTime = signInTime;
        this.autoRewarded = signInType.equals(ESignInType.REWARD) || autoRewarded;
        this.signInType = signInType;
    }

    public SignInPacket(FriendlyByteBuf buf) {
        this.signInTime = buf.readUtf();
        this.autoRewarded = buf.readBoolean();
        this.signInType = ESignInType.valueOf(buf.readInt());
    }

    public void toBytes(@NonNull FriendlyByteBuf buf) {
        buf.writeUtf(signInTime);
        buf.writeBoolean(autoRewarded);
        buf.writeInt(signInType.getCode());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SignInPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) {
                    RewardManager.signIn(player, packet);
                }
            });
        }
    }
}
