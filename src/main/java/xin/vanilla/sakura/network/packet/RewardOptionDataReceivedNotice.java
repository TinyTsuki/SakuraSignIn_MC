package xin.vanilla.sakura.network.packet;

import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.Component;

public class RewardOptionDataReceivedNotice implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "reward_option_data_received");

    private final boolean success;

    public RewardOptionDataReceivedNotice(boolean success) {
        this.success = success;
    }

    public RewardOptionDataReceivedNotice(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
    }

    public void write(@NonNull FriendlyByteBuf buf) {
        buf.writeBoolean(this.success);
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(RewardOptionDataReceivedNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.workHandler().execute(() -> {
                NotificationManager.Notification notification;
                if (packet.success) {
                    notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EI18nType.MESSAGE, "reward_option_upload_success"));
                } else {
                    notification = NotificationManager.Notification.ofComponentWithBlack(Component.translatable(EI18nType.MESSAGE, "reward_option_upload_failed")).setBgColor(0x88FF5555);
                }
                NotificationManager.get().addNotification(notification);
            });
        }
    }
}
