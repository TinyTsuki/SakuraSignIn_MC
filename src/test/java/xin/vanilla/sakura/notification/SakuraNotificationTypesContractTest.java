package xin.vanilla.sakura.notification;

import org.junit.Test;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 帮助与命令反馈默认保持原版聊天，同时允许客户端按通知类型自定义。
 */
public class SakuraNotificationTypesContractTest {

    @Test
    public void helpDefaultsToVanillaChat() {
        SakuraNotificationTypes.registerServerTypes();

        assertEquals(EnumNotificationTypeDisplayMode.VANILLA_CHAT,
                SakuraNotificationTypes.defaultDisplay(SakuraNotificationTypes.HELP));
        assertTrue(SakuraNotificationTypes.HELP.startsWith("sakura_sign_in."));
    }
}
