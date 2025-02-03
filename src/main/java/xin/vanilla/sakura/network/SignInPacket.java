package xin.vanilla.sakura.network;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.rewards.RewardManager;

import java.util.function.Supplier;

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

    public SignInPacket(PacketBuffer buf) {
        this.signInTime = buf.readUtf();
        this.autoRewarded = buf.readBoolean();
        this.signInType = ESignInType.valueOf(buf.readInt());
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeUtf(signInTime);
        buf.writeBoolean(autoRewarded);
        buf.writeInt(signInType.getCode());
    }

    public static void handle(SignInPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                RewardManager.signIn(player, packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
