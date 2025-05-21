package xin.vanilla.sakura.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CommonConfig {
	public static final ModConfigSpec COMMON_CONFIG;
	// region 自定义指令

	/**
	 * 命令前缀
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_PREFIX;

	/**
	 * 签到
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_SIGN_IN;

	/**
	 * 签到并领取奖励
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_SIGN_IN_EX;

	/**
	 * 领取奖励
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_REWARD;

	/**
	 * CDK
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_CDK;

	/**
	 * 补签卡
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_CARD;

	/**
	 * 设置语言
	 */
	public static final ModConfigSpec.ConfigValue<String> COMMAND_LANGUAGE;

	// endregion 自定义指令

	// region 简化指令

	/**
	 * 签到
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_SIGN_IN;

	/**
	 * 签到并领取奖励
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_SIGN_IN_EX;

	/**
	 * 领取奖励
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_REWARD;

	/**
	 * CDK
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_CDK;

	/**
	 * 补签卡
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_CARD;

	/**
	 * 设置语言
	 */
	public static final ModConfigSpec.BooleanValue CONCISE_LANGUAGE;

	// endregion 简化指令

	static {
		ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
		// 定义自定义指令配置
		{
			COMMON_BUILDER.comment("Custom Command Settings, don't add prefix '/'", "自定义指令，请勿添加前缀'/'").push("command");

			// 命令前缀
			COMMAND_PREFIX = COMMON_BUILDER
					.comment("The prefix of the command, please only use English characters and underscores, otherwise it may cause problems.",
							"指令前缀，请仅使用英文字母及下划线，否则可能会出现问题。")
					.define("commandPrefix", "sakura");

			// 签到
			COMMAND_SIGN_IN = COMMON_BUILDER
					.comment("This command is used to sign In or make up for a specific date."
							, "签到或补签的指令。")
					.define("commandSignIn", "sign");

			// 签到并领取奖励
			COMMAND_SIGN_IN_EX = COMMON_BUILDER
					.comment("This command is used to sign In or make up for a specific date and claim the rewards."
							, "签到并领取奖励的指令。")
					.define("commandSignInEx", "signex");

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

			// 设置语言
			COMMAND_LANGUAGE = COMMON_BUILDER
					.comment("This command is used to set the language."
							, "设置语言的指令。")
					.define("commandLanguage", "language");

			COMMON_BUILDER.pop();
		}

		// 定义简化指令
		{
			COMMON_BUILDER.comment("Concise Command Settings", "简化指令").push("concise");

			CONCISE_SIGN_IN = COMMON_BUILDER
					.comment("Enable or disable the concise version of the 'Sign In or make up for a specific date' command.",
							"是否启用无前缀版本的 '签到' 指令。")
					.define("conciseSignIn", true);

			CONCISE_SIGN_IN_EX = COMMON_BUILDER
					.comment("Enable or disable the concise version of the 'Sign In or make up for a specific date and claim the rewards' command.",
							"是否启用无前缀版本的 '签到并领取奖励' 指令。")
					.define("conciseSignInEx", true);

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

			CONCISE_LANGUAGE = COMMON_BUILDER
					.comment("Enable or disable the concise version of the 'Set the language' command.",
							"是否启用无前缀版本的 '设置语言' 指令。")
					.define("conciseLanguage", false);

			COMMON_BUILDER.pop();
		}
		COMMON_CONFIG = COMMON_BUILDER.build();
	}
}
