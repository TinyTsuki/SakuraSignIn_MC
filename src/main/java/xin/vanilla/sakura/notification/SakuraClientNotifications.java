package xin.vanilla.sakura.notification;

import xin.vanilla.banira.api.client.notification.BaniraNotifications;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.NotificationData;
import xin.vanilla.banira.common.enums.EnumMoveType;
import xin.vanilla.banira.common.enums.EnumNotificationStyle;
import xin.vanilla.banira.common.enums.EnumPosition;

import javax.annotation.Nonnull;

/**
 * Sakura 客户端通知入口，具体绘制、动画和记录统一交由 Banira。
 */
public final class SakuraClientNotifications {

    private static final long DEFAULT_DURATION_MS = 5000L;
    private static final long SYNC_DURATION_MS = 2000L;

    private SakuraClientNotifications() {
    }

    public static void show(@Nonnull Component message, @Nonnull String typeId) {
        show(message, typeId, EnumNotificationStyle.NORMAL);
    }

    public static void success(@Nonnull Component message, @Nonnull String typeId) {
        show(message, typeId, EnumNotificationStyle.SUCCESS);
    }

    public static void syncSuccess(@Nonnull Component message, @Nonnull String typeId) {
        show(message, typeId, EnumNotificationStyle.SUCCESS, SYNC_DURATION_MS);
    }

    public static void warning(@Nonnull Component message, @Nonnull String typeId) {
        show(message, typeId, EnumNotificationStyle.WARNING);
    }

    public static void error(@Nonnull Component message, @Nonnull String typeId) {
        show(message, typeId, EnumNotificationStyle.ERROR);
    }

    private static void show(Component message, String typeId, EnumNotificationStyle style) {
        show(message, typeId, style, DEFAULT_DURATION_MS);
    }

    private static void show(Component message, String typeId, EnumNotificationStyle style, long duration) {
        BaniraNotifications.show(NotificationData.of(
                message,
                EnumPosition.TOP_CENTER,
                EnumMoveType.AUTO,
                duration,
                style,
                typeId
        ).themed(true));
    }
}
