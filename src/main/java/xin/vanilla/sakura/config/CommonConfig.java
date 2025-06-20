package xin.vanilla.sakura.config;


import net.minecraftforge.common.ForgeConfigSpec;

public class CommonConfig {
    public static final ForgeConfigSpec COMMON_CONFIG;

    // region 自定义指令

    /**
     * 命令前缀
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_PREFIX;

    /**
     * 设置语言
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_LANGUAGE;

    /**
     * 设置虚拟权限
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_VIRTUAL_OP;

    /**
     * 签到
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_SIGN;

    /**
     * 签到并领取奖励
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_SIGNEX;

    /**
     * 领取奖励
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_REWARD;

    /**
     * CDK
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_CDK;

    /**
     * 补签卡
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_CARD;

    // endregion 自定义指令


    // region 简化指令

    /**
     * 设置语言
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_LANGUAGE;

    /**
     * 设置虚拟权限
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_VIRTUAL_OP;

    /**
     * 签到
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_SIGN;

    /**
     * 签到并领取奖励
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_SIGNEX;

    /**
     * 领取奖励
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_REWARD;

    /**
     * CDK
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_CDK;

    /**
     * 补签卡
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_CARD;

    // endregion 简化指令


    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
        // 定义自定义指令配置
        {
            COMMON_BUILDER.comment("Custom Command Settings, don't add prefix '/'", "自定义指令，请勿添加前缀'/'").push("command");

            // 命令前缀
            COMMAND_PREFIX = COMMON_BUILDER
                    .comment("The prefix of the command, please only use English characters and underscores, otherwise it may cause problems.",
                            "指令前缀，请仅使用英文字母及下划线，否则可能会出现问题。")
                    .define("commandPrefix", "sakura");

            // 设置语言
            COMMAND_LANGUAGE = COMMON_BUILDER
                    .comment("This command is used to set the language."
                            , "设置语言的指令。")
                    .define("commandLanguage", "language");

            // 设置虚拟权限
            COMMAND_VIRTUAL_OP = COMMON_BUILDER
                    .comment("The command to set virtual permission."
                            , "设置虚拟权限的指令。")
                    .define("commandVirtualOp", "opv");

            // 签到
            COMMAND_SIGN = COMMON_BUILDER
                    .comment("This command is used to sign In or make up for a specific date."
                            , "签到或补签的指令。")
                    .define("commandSign", "sign");

            // 签到并领取奖励
            COMMAND_SIGNEX = COMMON_BUILDER
                    .comment("This command is used to sign In or make up for a specific date and claim the rewards."
                            , "签到并领取奖励的指令。")
                    .define("commandSignEx", "signex");

            // 领取奖励
            COMMAND_REWARD = COMMON_BUILDER
                    .comment("This command is used to claim the rewards."
                            , "领取奖励的指令。")
                    .define("commandReward", "reward");

            // CDK
            COMMAND_CDK = COMMON_BUILDER
                    .comment("This command is used to claim the rewards by cdk."
                            , "领取兑换码奖励的指令。")
                    .define("commandCdk", "cdk");

            // 补签卡
            COMMAND_CARD = COMMON_BUILDER
                    .comment("This command is used to get or set the Sign-in Card."
                            , "补签卡领取奖励的指令。")
                    .define("commandCard", "card");

            COMMON_BUILDER.pop();
        }

        // 定义简化指令
        {
            COMMON_BUILDER.comment("Concise Command Settings", "简化指令").push("concise");

            CONCISE_LANGUAGE = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Set the language' command.",
                            "是否启用无前缀版本的 '设置语言' 指令。")
                    .define("conciseLanguage", false);

            CONCISE_VIRTUAL_OP = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Set virtual permission' command.",
                            "是否启用无前缀版本的 '设置虚拟权限' 指令。")
                    .define("conciseVirtualOp", false);

            CONCISE_SIGN = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Sign In or make up for a specific date' command.",
                            "是否启用无前缀版本的 '签到' 指令。")
                    .define("conciseSign", true);

            CONCISE_SIGNEX = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Sign In or make up for a specific date and claim the rewards' command.",
                            "是否启用无前缀版本的 '签到并领取奖励' 指令。")
                    .define("conciseSignEx", true);

            CONCISE_REWARD = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Claim the rewards' command.",
                            "是否启用无前缀版本的 '领取奖励' 指令。")
                    .define("conciseReward", true);

            CONCISE_CDK = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Claim the rewards by cdk' command.",
                            "是否启用无前缀版本的 '领取兑换码奖励' 指令。")
                    .define("conciseCdk", true);

            CONCISE_CARD = COMMON_BUILDER
                    .comment("Enable or disable the concise version of the 'Get or set the Sign-in Card' command.",
                            "是否启用无前缀版本的 '补签卡领取奖励' 指令。")
                    .define("conciseCard", false);

            COMMON_BUILDER.pop();
        }
        COMMON_CONFIG = COMMON_BUILDER.build();
    }
}
