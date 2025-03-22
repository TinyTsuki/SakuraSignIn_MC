package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.rewards.RewardManager;

@Getter
public class SignInPacket {
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

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(signInTime);
        buf.writeBoolean(autoRewarded);
        buf.writeInt(signInType.getCode());
    }

    public static void handle(SignInPacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                RewardManager.signIn(player, packet);
            }
        });
        ctx.setPacketHandled(true);
    }
}
