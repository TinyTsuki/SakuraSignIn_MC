package xin.vanilla.mc.config;

import net.minecraftforge.common.ForgeConfigSpec;

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

        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }
}
