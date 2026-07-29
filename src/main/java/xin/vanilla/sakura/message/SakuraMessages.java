package xin.vanilla.sakura.message;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.MessageUtils;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * Sakura 服务端消息的唯一出口，按玩家能力选择通知或原版聊天。
 */
public final class SakuraMessages {
    private SakuraMessages() {
    }

    public static void send(ServerPlayerEntity player, Component message) {
        send(player, message, SakuraNotificationTypes.COMMAND_FEEDBACK);
    }

    public static void send(ServerPlayerEntity player, Component message, String notificationType) {
        Component payload = message.clone().languageCode(SakuraUtils.getPlayerLanguage(player));
        if (BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)) {
            MessageUtils.sendNotification(player, payload, notificationType);
        } else {
            MessageUtils.sendMessage(player, payload);
        }
    }

    public static void broadcast(ServerPlayerEntity sender, Component message) {
        for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
            send(player, message, SakuraNotificationTypes.ADMIN_BROADCAST);
        }
    }
}
