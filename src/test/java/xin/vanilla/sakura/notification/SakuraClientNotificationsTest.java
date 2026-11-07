package xin.vanilla.sakura.notification;

import org.junit.Test;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.NotificationData;
import xin.vanilla.banira.platform.BaniraNotificationService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SakuraClientNotificationsTest {
    @Test
    public void syncSuccessIsBriefWithoutShorteningOtherFeedback() {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        BaniraPlatform previous = BaniraPlatforms.get();
        List<NotificationData> shown = new ArrayList<>();
        BaniraNotificationService notifications = new BaniraNotificationService() {
            public void show(Component message) {
                throw new AssertionError("Expected structured notification");
            }

            public void show(NotificationData data) {
                shown.add(data);
            }

            public void show(NotificationData data, boolean fromNetwork) {
                shown.add(data);
            }
        };
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) ->
                        method.getName().equals("notificationService") ? notifications : method.invoke(previous, args)));
        try {
            Component message = xin.vanilla.sakura.SakuraComponent.get().literal("Synced");
            SakuraClientNotifications.syncSuccess(message, SakuraNotificationTypes.CONFIG);
            SakuraClientNotifications.syncSuccess(message, SakuraNotificationTypes.REWARD);
            SakuraClientNotifications.success(message, SakuraNotificationTypes.REWARD);
            SakuraClientNotifications.error(message, SakuraNotificationTypes.CONFIG);
            assertEquals(com.google.common.collect.ImmutableList.of(2000L, 2000L, 5000L, 5000L), shown.stream().map(NotificationData::durationTime).collect(java.util.stream.Collectors.toList()));
        } finally {
            BaniraPlatforms.install(previous);
        }
    }
}
