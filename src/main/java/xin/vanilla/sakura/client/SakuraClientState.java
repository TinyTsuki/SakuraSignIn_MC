package xin.vanilla.sakura.client;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 客户端会话与主题状态，不再污染加载器入口类。
 */
public final class SakuraClientState {
    @Getter
    @Setter
    private static boolean enabled;
    @Getter
    @Setter
    private static boolean rewardOptionBarOpened;
    @Getter
    @Setter
    private static Date calendarCurrentDate;
    @Getter
    @Setter
    private static ResourceLocation themeTexture;
    @Getter
    @Setter
    private static TextureCoordinate themeTextureCoordinate;
    @Getter
    @Setter
    private static String activeThemeId = "sakura";
    @Getter
    @Setter
    private static boolean specialThemeVariant;
    @Getter
    @Setter
    private static List<AdvancementData> advancementData = Collections.emptyList();
    @Getter
    @Setter
    private static int permissionLevel;
    @Getter
    @Setter
    private static Map<String, String> calendarNames = Collections.emptyMap();
    @Getter
    private static final KeyValue<String, String> clientServerTime = new KeyValue<>(
            DateUtils.toDateTimeString(new Date(0)),
            DateUtils.toString(new Date(0))
    );

    private SakuraClientState() {
    }

    public static void clearSession() {
        enabled = false;
        calendarCurrentDate = null;
        advancementData = Collections.emptyList();
        permissionLevel = 0;
        calendarNames = Collections.emptyMap();
    }
}
