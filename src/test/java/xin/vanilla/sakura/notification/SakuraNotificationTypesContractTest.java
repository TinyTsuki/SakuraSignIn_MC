package xin.vanilla.sakura.notification;

import org.junit.Test;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 帮助与命令反馈默认保持原版聊天，同时允许客户端按通知类型自定义。
 */
public class SakuraNotificationTypesContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void helpDefaultsToVanillaChat() {
        SakuraNotificationTypes.registerServerTypes();

        assertEquals(EnumNotificationTypeDisplayMode.VANILLA_CHAT,
                SakuraNotificationTypes.defaultDisplay(SakuraNotificationTypes.HELP));
        assertTrue(SakuraNotificationTypes.HELP.startsWith("sakura_sign_in."));
    }

    @Test
    public void clientNotificationsAreDelegatedToBanira() throws IOException {
        String facade = new String(Files.readAllBytes(
                MAIN.resolve("notification/SakuraClientNotifications.java")),
                StandardCharsets.UTF_8);
        String events = new String(Files.readAllBytes(
                MAIN.resolve("event/ClientEventHandler.java")),
                StandardCharsets.UTF_8);

        assertTrue(facade.contains("BaniraNotifications.show"));
        assertTrue(facade.contains("NotificationData.of"));
        assertFalse(events.contains("NotificationManager"));
        assertFalse(Files.exists(MAIN.resolve("screen/component/NotificationManager.java")));
    }
}
