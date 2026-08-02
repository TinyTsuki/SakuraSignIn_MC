package xin.vanilla.sakura.notification;

import xin.vanilla.banira.api.notification.BaniraNotificationTypes;
import xin.vanilla.banira.common.enums.EnumMoveType;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.sakura.SakuraSignIn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sakura 的通知分类，服务端与各加载器分支共用相同 id 和默认语义。
 */
public final class SakuraNotificationTypes {
    private static final String PREFIX = SakuraSignIn.MODID + ".";
    private static final Map<String, EnumNotificationTypeDisplayMode> DEFAULT_DISPLAYS;

    public static final String HELP = PREFIX + "help";
    public static final String COMMAND_FEEDBACK = PREFIX + "command_feedback";
    public static final String SIGN_IN = PREFIX + "sign_in";
    public static final String REWARD = PREFIX + "reward";
    public static final String CONFIG = PREFIX + "config";
    public static final String CDK = PREFIX + "cdk";
    public static final String ADMIN_BROADCAST = PREFIX + "admin_broadcast";

    static {
        Map<String, EnumNotificationTypeDisplayMode> defaults = new LinkedHashMap<>();
        defaults.put(HELP, EnumNotificationTypeDisplayMode.VANILLA_CHAT);
        defaults.put(COMMAND_FEEDBACK, EnumNotificationTypeDisplayMode.VANILLA_CHAT);
        defaults.put(SIGN_IN, EnumNotificationTypeDisplayMode.OVERLAY);
        defaults.put(REWARD, EnumNotificationTypeDisplayMode.OVERLAY);
        defaults.put(CONFIG, EnumNotificationTypeDisplayMode.OVERLAY);
        defaults.put(CDK, EnumNotificationTypeDisplayMode.OVERLAY);
        defaults.put(ADMIN_BROADCAST, EnumNotificationTypeDisplayMode.VANILLA_CHAT);
        DEFAULT_DISPLAYS = Collections.unmodifiableMap(defaults);
    }

    private SakuraNotificationTypes() {
    }

    public static void registerServerTypes() {
        DEFAULT_DISPLAYS.forEach((typeId, display) -> BaniraNotificationTypes.register(
                typeId, EnumPosition.TOP_CENTER, EnumMoveType.AUTO, display
        ));
    }

    public static EnumNotificationTypeDisplayMode defaultDisplay(String typeId) {
        return DEFAULT_DISPLAYS.get(typeId);
    }

    public static Iterable<String> all() {
        return DEFAULT_DISPLAYS.keySet();
    }
}
