package xin.vanilla.sakura.config;

import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.GLFWKeyHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端配置
 */
public class ClientConfig {
    public static final ForgeConfigSpec CLIENT_CONFIG;
    /**
     * 主题设置
     */
    public static final ForgeConfigSpec.ConfigValue<String> THEME;
    /**
     * 是否使用内置主题特殊图标
     */
    public static final ForgeConfigSpec.BooleanValue SPECIAL_THEME;
    /**
     * 签到页面显示上月奖励
     */
    public static final ForgeConfigSpec.BooleanValue SHOW_LAST_REWARD;
    /**
     * 签到页面显示下月奖励
     */
    public static final ForgeConfigSpec.BooleanValue SHOW_NEXT_REWARD;
    /**
     * 自动领取
     */
    public static final ForgeConfigSpec.BooleanValue AUTO_REWARDED;

    /**
     * 背包界面签到按钮坐标
     */
    public static final ForgeConfigSpec.ConfigValue<String> INVENTORY_SIGN_IN_BUTTON_COORDINATE;

    /**
     * 背包界面奖励配置按钮坐标
     */
    public static final ForgeConfigSpec.ConfigValue<String> INVENTORY_REWARD_OPTION_BUTTON_COORDINATE;

    /**
     * 显示签到界面提示
     */
    public static final ForgeConfigSpec.BooleanValue SHOW_SIGN_IN_SCREEN_TIPS;

    /**
     * 主题参数
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> THEME_ARGS;

    // region 按键设置

    /**
     * 配置界面 - 复制
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_COPY;
    /**
     * 配置界面 - 粘贴
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_PASTE;
    /**
     * 配置界面 - 裁剪
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_CUT;
    /**
     * 配置界面 - 删除
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_DELETE;
    /**
     * 配置界面 - 撤销
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_UNDO;
    /**
     * 配置界面 - 重做
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_REDO;
    /**
     * 配置界面 - 保存
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_OPTION_SAVE;

    /**
     * 签到界面 - 签到
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_SIGN_IN;
    /**
     * 签到界面 - 补签
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_RE_SIGN_IN;
    /**
     * 签到界面 - 领取奖励
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_REWARD;
    /**
     * 签到界面 - 上月
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_LAST_MONTH;
    /**
     * 签到界面 - 下月
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_NEXT_MONTH;

    /**
     * 签到界面 - 去年
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_LAST_YEAR;
    /**
     * 签到界面 - 明年
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KEY_SIGN_NEXT_YEAR;

    // endregion 按键设置

    static {
        ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

        // 定义客户端配置项
        CLIENT_BUILDER.comment("Client Settings").push("client");

        // 主题
        THEME = CLIENT_BUILDER
                .comment("theme textures path, can be external path: config/sakura_sign_in/themes/your_theme.png"
                        , "主题材质路径，可为外部路径： config/sakura_sign_in/themes/your_theme.png")
                .define("theme", "textures/gui/sign_in_calendar_sakura.png");

        // 内置主题特殊图标
        SPECIAL_THEME = CLIENT_BUILDER
                .comment("Whether or not to use the built-in theme special icons."
                        , "是否使用内置主题特殊图标。")
                .define("specialTheme", true);

        // 签到页面显示上月奖励
        SHOW_LAST_REWARD = CLIENT_BUILDER
                .comment("The sign-in page displays last month's rewards. Someone said it didn't look good on display."
                        , "签到页面是否显示上个月的奖励，有人说它显示出来不好看。")
                .define("showLastReward", false);

        // 签到页面显示下月奖励
        SHOW_NEXT_REWARD = CLIENT_BUILDER
                .comment("The sign-in page displays next month's rewards. Someone said it didn't look good on display."
                        , "签到页面是否显示下个月的奖励，有人说它显示出来不好看。")
                .define("showNextReward", false);

        // 自动领取
        AUTO_REWARDED = CLIENT_BUILDER
                .comment("Whether the rewards will be automatically claimed when you sign-in or re-sign-in."
                        , "签到或补签时是否自动领取奖励。")
                .define("autoRewarded", false);

        // 背包界面签到按钮坐标
        INVENTORY_SIGN_IN_BUTTON_COORDINATE = CLIENT_BUILDER
                .comment("The coordinate of the sign-in button in the inventory screen. If the coordinate is 0~1, it is the percentage position."
                        , "背包界面签到按钮坐标，若坐标为0~1之间的小数则为百分比位置。")
                .define("inventorySignInButtonCoordinate", "92,2");

        // 背包界面奖励配置按钮坐标
        INVENTORY_REWARD_OPTION_BUTTON_COORDINATE = CLIENT_BUILDER
                .comment("The coordinate of the reward option button in the inventory screen. If the coordinate is 0~1, it is the percentage position."
                        , "背包界面奖励配置按钮坐标，若坐标为0~1之间的小数则为百分比位置。")
                .define("inventoryRewardOptionButtonCoordinate", "72,2");

        SHOW_SIGN_IN_SCREEN_TIPS = CLIENT_BUILDER
                .comment("Whether or not to display a prompt for action when you open the sign-in screen."
                        , "打开签到页面时是否显示操作提示。")
                .define("showSignInScreenTips", true);

        THEME_ARGS = CLIENT_BUILDER
                .comment("Parameter list reserved for theme configuration; usually does not need to be modified.",
                        "这是留给主题写入使用的参数列表，一般情况下无需修改。")
                .defineList("themeArgs", new ArrayList<>(), s -> s instanceof String);

        // 按键设置
        {
            CLIENT_BUILDER.comment("Key Settings").push("key");

            {
                CLIENT_BUILDER.comment("Option").push("option");

                KEY_OPTION_COPY = CLIENT_BUILDER
                        .comment("Keys used to copy on the option screen",
                                "配置页面进行 复制操作 时所使用的按键")
                        .defineList("copy", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_C));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_C));
                        }}, s -> s instanceof String);

                KEY_OPTION_PASTE = CLIENT_BUILDER
                        .comment("Keys used to paste on the option screen",
                                "配置页面进行 粘贴操作 时所使用的按键")
                        .defineList("paste", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_V));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_V));
                        }}, s -> s instanceof String);

                KEY_OPTION_CUT = CLIENT_BUILDER
                        .comment("Keys used to cut on the option screen",
                                "配置页面进行 裁剪操作 时所使用的按键")
                        .defineList("cut", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_X));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_X));
                        }}, s -> s instanceof String);

                KEY_OPTION_DELETE = CLIENT_BUILDER
                        .comment("Keys used to delete on the option screen",
                                "配置页面进行 删除操作 时所使用的按键")
                        .defineList("delete", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_DELETE));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_Y));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL) + "+" + GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_RIGHT));
                        }}, s -> s instanceof String);

                KEY_OPTION_UNDO = CLIENT_BUILDER
                        .comment("Keys used to undo on the option screen",
                                "配置页面进行 撤销操作 时所使用的按键")
                        .defineList("undo", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_Z));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_Z));
                        }}, s -> s instanceof String);

                KEY_OPTION_REDO = CLIENT_BUILDER
                        .comment("Keys used to redo on the option screen",
                                "配置页面进行 重做操作 时所使用的按键")
                        .defineList("redo", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_LEFT_SHIFT, GLFWKey.GLFW_KEY_Z));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_RIGHT_SHIFT, GLFWKey.GLFW_KEY_Z));
                        }}, s -> s instanceof String);

                KEY_OPTION_SAVE = CLIENT_BUILDER
                        .comment("Keys used to save on the option screen",
                                "配置页面进行 保存操作 时所使用的按键")
                        .defineList("save", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_CONTROL, GLFWKey.GLFW_KEY_S));
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT_CONTROL, GLFWKey.GLFW_KEY_S));
                        }}, s -> s instanceof String);

                CLIENT_BUILDER.pop();
            }

            {
                CLIENT_BUILDER.comment("Sign").push("sign");

                KEY_SIGN_SIGN_IN = CLIENT_BUILDER
                        .comment("Keys used to sign in on the sign-in screen",
                                "签到页面进行 签到操作 时所使用的按键")
                        .defineList("signIn", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_ENTER));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT));
                        }}, s -> s instanceof String);

                KEY_SIGN_RE_SIGN_IN = CLIENT_BUILDER
                        .comment("Keys used to re-sign in on the sign-in screen",
                                "签到页面进行 补签操作 时所使用的按键")
                        .defineList("reSignIn", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT, GLFWKey.GLFW_KEY_ENTER));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_RIGHT));
                        }}, s -> s instanceof String);

                KEY_SIGN_REWARD = CLIENT_BUILDER
                        .comment("Keys used to claim rewards on the sign-in screen",
                                "签到页面进行 领取奖励操作 时所使用的按键")
                        .defineList("reward", new ArrayList<String>() {{
                            add("LeftShift+Enter");
                            add("MouseRight");
                        }}, s -> s instanceof String);

                KEY_SIGN_LAST_MONTH = CLIENT_BUILDER
                        .comment("Keys used to switch to the last month on the sign-in screen",
                                "签到页面进行 切换到上个月 时所使用的按键")
                        .defineList("lastMonth", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT));
                        }}, s -> s instanceof String);

                KEY_SIGN_NEXT_MONTH = CLIENT_BUILDER
                        .comment("Keys used to switch to the next month on the sign-in screen",
                                "签到页面进行 切换到下个月 时所使用的按键")
                        .defineList("nextMonth", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_RIGHT));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT));
                        }}, s -> s instanceof String);

                KEY_SIGN_LAST_YEAR = CLIENT_BUILDER
                        .comment("Keys used to switch to the last year on the sign-in screen",
                                "签到页面进行 切换到上一年 时所使用的按键")
                        .defineList("lastYear", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_UP));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT));
                        }}, s -> s instanceof String);

                KEY_SIGN_NEXT_YEAR = CLIENT_BUILDER
                        .comment("Keys used to switch to the next year on the sign-in screen",
                                "签到页面进行 切换到下一年 时所使用的按键")
                        .defineList("nextYear", new ArrayList<String>() {{
                            add(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_DOWN));
                            add(GLFWKeyHelper.getMouseDisplayString(GLFWKey.GLFW_MOUSE_BUTTON_LEFT));
                        }}, s -> s instanceof String);

                CLIENT_BUILDER.pop();
            }

            CLIENT_BUILDER.pop();

        }

        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }
}
