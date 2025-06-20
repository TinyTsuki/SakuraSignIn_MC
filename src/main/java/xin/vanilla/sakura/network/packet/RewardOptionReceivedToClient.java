package xin.vanilla.sakura.network.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.Component;

import java.util.function.Supplier;

public class RewardOptionReceivedToClient {
    private final boolean success;

    public RewardOptionReceivedToClient(boolean success) {
        this.success = success;
    }

    public RewardOptionReceivedToClient(PacketBuffer buf) {
        this.success = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeBoolean(this.success);
    }

    public static void handle(RewardOptionReceivedToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            NotificationManager.Notification notification;
            if (packet.success) {
                notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EnumI18nType.MESSAGE, "reward_option_upload_success"));
            } else {
                notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EnumI18nType.MESSAGE, "reward_option_upload_failed")).setBgArgb(0x88FF5555);
            }
            NotificationManager.get().addNotification(notification);
        });
        ctx.get().setPacketHandled(true);
    }
}
