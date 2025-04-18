package xin.vanilla.sakura.network.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.Component;

import java.util.function.Supplier;

public class RewardOptionDataReceivedNotice {
    private final boolean success;

    public RewardOptionDataReceivedNotice(boolean success) {
        this.success = success;
    }

    public RewardOptionDataReceivedNotice(PacketBuffer buf) {
        this.success = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeBoolean(this.success);
    }

    public static void handle(RewardOptionDataReceivedNotice packet, Supplier<NetworkEvent.Context> ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.get().enqueueWork(() -> {
            NotificationManager.Notification notification;
            if (packet.success) {
                notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EI18nType.MESSAGE, "reward_option_upload_success"));
            } else {
                notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EI18nType.MESSAGE, "reward_option_upload_failed")).setBgColor(0x88FF5555);
            }
            NotificationManager.get().addNotification(notification);
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.get().setPacketHandled(true);
    }
}
