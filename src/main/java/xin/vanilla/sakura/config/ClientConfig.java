package xin.vanilla.sakura.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigData;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigScope;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.common.config.annotation.ConfigEntry;
import xin.vanilla.banira.api.client.theme.BaniraThemeMode;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.access.ClientConfigAccess;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.GLFWKeyHelper;

import java.util.Arrays;
import java.util.List;

/**
 * 仅客户端使用的显示与快捷键设置。
 */
@Config(name = SakuraSignIn.MODID + "-client", type = ConfigScope.CLIENT)
public class ClientConfig implements ConfigData {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "签到界面显示设置", en_us = "Sign-in screen display settings")
    private DisplayCategory display = new DisplayCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "奖励编辑快捷键", en_us = "Reward editor shortcuts")
    private RewardKeysCategory rewardKeys = new RewardKeysCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "签到界面快捷键", en_us = "Sign-in screen shortcuts")
    private SignKeysCategory signKeys = new SignKeysCategory();

    public static RootView get() {
        return ClientConfigAccess.root(BaniraConfig.holder(ClientConfig.class));
    }

    public static void save() {
        ConfigHolder holder = BaniraConfig.holder(ClientConfig.class);
        if (holder != null) {
            holder.save();
        }
    }

    public interface RootView {
        DisplayView display();
        RewardKeysView rewardKeys();
        SignKeysView signKeys();
        ConfigHolder holder();
    }

    public interface DisplayView {
        BaniraThemeMode interfaceThemeMode();
        String themeId();
        DisplayView themeId(String value);
        boolean specialVariant();
        DisplayView specialVariant(boolean value);
        boolean showLastReward();
        boolean showNextReward();
        boolean autoRewarded();
        boolean showSignInScreenTips();
        DisplayView showSignInScreenTips(boolean value);
    }

    public interface RewardKeysView {
        List<String> copy();
        List<String> paste();
        List<String> cut();
        List<String> delete();
        List<String> undo();
        List<String> redo();
    }

    public interface SignKeysView {
        List<String> lastMonth();
        List<String> nextMonth();
        List<String> lastYear();
        List<String> nextYear();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class DisplayCategory {
        @ConfigEntry.Gui.Tooltip(
                zh_cn = "选择樱花签界面使用的 Banira 配色\n可跟随香草志、随季节自动或固定为某个季节",
                en_us = "Choose the Banira color theme used by Sakura Sign-In screens\nFollow Banira, follow the current season, or lock a season")
        private BaniraThemeMode interfaceThemeMode = BaniraThemeMode.SPRING;
        @ConfigEntry.Gui.Tooltip(zh_cn = "内置主题 ID", en_us = "Built-in theme ID")
        private String themeId = "sakura";
        @ConfigEntry.Gui.Tooltip(zh_cn = "使用主题的特殊签到图标", en_us = "Use the theme's alternate sign-in icons")
        private boolean specialVariant = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "在当月日历前方显示上月奖励", en_us = "Show previous-month rewards before the current month")
        private boolean showLastReward = false;
        @ConfigEntry.Gui.Tooltip(zh_cn = "在当月日历后方显示下月奖励", en_us = "Show next-month rewards after the current month")
        private boolean showNextReward = false;
        @ConfigEntry.Gui.Tooltip(zh_cn = "签到后自动领取对应奖励", en_us = "Automatically claim rewards after signing in")
        private boolean autoRewarded = false;
        @ConfigEntry.Gui.Tooltip(zh_cn = "首次打开签到界面时显示操作说明", en_us = "Show instructions when opening the sign-in screen for the first time")
        private boolean showSignInScreenTips = true;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class RewardKeysCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "复制选中奖励的快捷键", en_us = "Keyboard shortcuts for copying selected rewards")
        private List<String> copy = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_C),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_C));
        @ConfigEntry.Gui.Tooltip(zh_cn = "粘贴奖励的快捷键", en_us = "Keyboard shortcuts for pasting rewards")
        private List<String> paste = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_V),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_V));
        @ConfigEntry.Gui.Tooltip(zh_cn = "剪切选中奖励的快捷键", en_us = "Keyboard shortcuts for cutting selected rewards")
        private List<String> cut = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_X),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_X));
        @ConfigEntry.Gui.Tooltip(zh_cn = "删除选中奖励的快捷键", en_us = "Keyboard shortcuts for deleting selected rewards")
        private List<String> delete = Arrays.asList(
                key(GLFWKey.GLFW_KEY_DELETE));
        @ConfigEntry.Gui.Tooltip(zh_cn = "撤销奖励配置修改的快捷键", en_us = "Keyboard shortcuts for undoing reward changes")
        private List<String> undo = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_Z),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_Z));
        @ConfigEntry.Gui.Tooltip(zh_cn = "重做奖励配置修改的快捷键", en_us = "Keyboard shortcuts for redoing reward changes")
        private List<String> redo = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_LEFT_SHIFT, GLFWKey.GLFW_KEY_Z),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_RIGHT_SHIFT, GLFWKey.GLFW_KEY_Z));
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SignKeysCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "切换到上个月的快捷键", en_us = "Keyboard shortcuts for the previous month")
        private List<String> lastMonth = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT));
        @ConfigEntry.Gui.Tooltip(zh_cn = "切换到下个月的快捷键", en_us = "Keyboard shortcuts for the next month")
        private List<String> nextMonth = Arrays.asList(
                key(GLFWKey.GLFW_KEY_RIGHT));
        @ConfigEntry.Gui.Tooltip(zh_cn = "切换到上一年的快捷键", en_us = "Keyboard shortcuts for the previous year")
        private List<String> lastYear = Arrays.asList(
                key(GLFWKey.GLFW_KEY_UP));
        @ConfigEntry.Gui.Tooltip(zh_cn = "切换到下一年的快捷键", en_us = "Keyboard shortcuts for the next year")
        private List<String> nextYear = Arrays.asList(
                key(GLFWKey.GLFW_KEY_DOWN));
    }

    private static String key(int... keys) {
        return GLFWKeyHelper.getKeyDisplayString(keys);
    }
}
