package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;

@Getter
public class SignInPacket implements INetworkPacket {
    private final String signInTime;
    private final boolean autoRewarded;
    private final ESignInType signInType;

    public SignInPacket(String signInTime, boolean autoRewarded, ESignInType signInType) {
        this.signInTime = signInTime;
        this.autoRewarded = signInType.equals(ESignInType.REWARD) || autoRewarded;
        this.signInType = signInType;
    }

    public SignInPacket(BaniraPacketBuffer buf) {
        this.signInTime = buf.readUtf();
        this.autoRewarded = buf.readBoolean();
        this.signInType = ESignInType.valueOf(buf.readInt());
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeUtf(signInTime);
        buf.writeBoolean(autoRewarded);
        buf.writeInt(signInType.getCode());
    }

    public static void handle(SignInPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.senderAs(ServerPlayerEntity.class);
            if (player != null) {
                RewardManager.signIn(player, packet);
                Date signInDate = DateUtils.format(packet.getSignInTime());
                if (signInDate != null) {
                    SakuraNetwork.syncMonth(player, signInDate);
                }
            }
        });
        ctx.markHandled();
    }
}
