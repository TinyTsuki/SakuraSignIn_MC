package xin.vanilla.sakura.message;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumNotificationStyle;
import xin.vanilla.banira.common.util.MessageUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Sakura 服务端消息的唯一出口，按玩家能力选择通知或原版聊天。
 */
public final class SakuraMessages {
    private SakuraMessages() {
    }

    public static void send(ServerPlayerEntity player, Component message) {
        send(player, message, SakuraNotificationTypes.COMMAND_FEEDBACK);
    }

    public static void success(ServerPlayerEntity player, Component message) {
        success(player, message, SakuraNotificationTypes.COMMAND_FEEDBACK);
    }

    public static void send(ServerPlayerEntity player, Component message, String notificationType) {
        send(player, message, EnumNotificationStyle.NORMAL, notificationType);
    }

    public static void success(ServerPlayerEntity player, Component message, String notificationType) {
        send(player, message, EnumNotificationStyle.SUCCESS, notificationType);
    }

    public static void send(ServerPlayerEntity player, Component message,
                            EnumNotificationStyle style, String notificationType) {
        Component payload = message.clone().languageCode(SakuraUtils.getPlayerLanguage(player));
        MessageUtils.sendNotification(player, payload, style, notificationType);
    }

    public static void successBatch(ServerPlayerEntity player, Component prefix,
                                    List<Component> entries, String notificationType) {
        String language = SakuraUtils.getPlayerLanguage(player);
        List<Component> payloads = new ArrayList<>(entries.size());
        for (Component entry : entries) {
            payloads.add(entry.clone().languageCode(language));
        }
        Component payloadPrefix = prefix.clone().languageCode(language);
        if (!entries.isEmpty()) {
            payloadPrefix.append(", ");
        }
        MessageUtils.sendNotificationBatch(player, payloadPrefix, payloads,
                SakuraComponent.get().literal(", ").languageCode(language),
                EnumNotificationStyle.SUCCESS, notificationType);
    }

    public static void broadcast(ServerPlayerEntity sender, Component message) {
        for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
            send(player, message, SakuraNotificationTypes.ADMIN_BROADCAST);
        }
    }
}
