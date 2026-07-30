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
        private boolean showLastReward = false;
        private boolean showNextReward = false;
        private boolean autoRewarded = false;
        private boolean showSignInScreenTips = true;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class RewardKeysCategory {
        private List<String> copy = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_C),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_C));
        private List<String> paste = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_V),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_V));
        private List<String> cut = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_X),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_X));
        private List<String> delete = Arrays.asList(
                key(GLFWKey.GLFW_KEY_DELETE));
        private List<String> undo = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_Z),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_Z));
        private List<String> redo = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_LEFT_SHIFT, GLFWKey.GLFW_KEY_Z),
                key(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_RIGHT_SHIFT, GLFWKey.GLFW_KEY_Z));
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SignKeysCategory {
        private List<String> lastMonth = Arrays.asList(
                key(GLFWKey.GLFW_KEY_LEFT));
        private List<String> nextMonth = Arrays.asList(
                key(GLFWKey.GLFW_KEY_RIGHT));
        private List<String> lastYear = Arrays.asList(
                key(GLFWKey.GLFW_KEY_UP));
        private List<String> nextYear = Arrays.asList(
                key(GLFWKey.GLFW_KEY_DOWN));
    }

    private static String key(int... keys) {
        return GLFWKeyHelper.getKeyDisplayString(keys);
    }
}
