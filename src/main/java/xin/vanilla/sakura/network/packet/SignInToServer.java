package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.enums.EnumSignInType;
import xin.vanilla.sakura.rewards.RewardManager;

import java.util.function.Supplier;

@Getter
public class SignInToServer {
    private final String signInTime;
    private final boolean autoRewarded;
    private final EnumSignInType signInType;

    public SignInToServer(String signInTime, boolean autoRewarded, EnumSignInType signInType) {
        this.signInTime = signInTime;
        this.autoRewarded = signInType.equals(EnumSignInType.REWARD) || autoRewarded;
        this.signInType = signInType;
    }

    public SignInToServer(PacketBuffer buf) {
        this.signInTime = buf.readUtf();
        this.autoRewarded = buf.readBoolean();
        this.signInType = EnumSignInType.valueOf(buf.readInt());
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeUtf(signInTime);
        buf.writeBoolean(autoRewarded);
        buf.writeInt(signInType.getCode());
    }

    public static void handle(SignInToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                RewardManager.signIn(player, packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
