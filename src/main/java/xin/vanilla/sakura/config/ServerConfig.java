package xin.vanilla.sakura.config;

import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.sakura.enums.EnumTimeCoolingMethod;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;

/**
 * 服务器配置
 */
public class ServerConfig {

    public static final ForgeConfigSpec SERVER_CONFIG;

    // region 基础配置

    /**
     * 是否启用补签卡
     */
    public static final ForgeConfigSpec.BooleanValue SIGN_IN_CARD;
    /**
     * 最大补签天数
     */
    public static final ForgeConfigSpec.IntValue RE_SIGN_IN_DAYS;
    /**
     * 补签仅基础奖励
     */
    public static final ForgeConfigSpec.BooleanValue SIGN_IN_CARD_ONLY_BASE_REWARD;

    /**
     * 签到时间冷却方式
     */
    public static final ForgeConfigSpec.ConfigValue<String> TIME_COOLING_METHOD;
    /**
     * 签到冷却刷新时间
     */
    public static final ForgeConfigSpec.DoubleValue TIME_COOLING_TIME;
    /**
     * 签到冷却刷新间隔
     */
    public static final ForgeConfigSpec.DoubleValue TIME_COOLING_INTERVAL;

    /**
     * 服务器时间
     */
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_TIME;
    /**
     * 实际时间
     */
    public static final ForgeConfigSpec.ConfigValue<String> ACTUAL_TIME;

    /**
     * 奖励领取是否受玩家幸运/霉运影响
     */
    public static final ForgeConfigSpec.BooleanValue REWARD_AFFECTED_BY_LUCK;
    /**
     * 连续签到奖励 天数达标后是否允许一直领取该标准奖励
     */
    public static final ForgeConfigSpec.BooleanValue CONTINUOUS_REWARDS_REPEATABLE;
    /**
     * 签到周期奖励 天数达标后是否允许一直领取该标准奖励
     */
    public static final ForgeConfigSpec.BooleanValue CYCLE_REWARDS_REPEATABLE;

    /**
     * 自动签到
     */
    public static final ForgeConfigSpec.BooleanValue AUTO_SIGN_IN;
    /**
     * 玩家签到数据同步网络包大小
     */
    public static final ForgeConfigSpec.IntValue PLAYER_DATA_SYNC_PACKET_SIZE;

    /**
     * 帮助指令信息头部内容
     */
    public static final ForgeConfigSpec.ConfigValue<String> HELP_HEADER;
    /**
     * 帮助信息每页显示的数量
     */
    public static final ForgeConfigSpec.IntValue HELP_INFO_NUM_PER_PAGE;
    /**
     * 服务器默认语言
     */
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    /**
     * 历史签到奖励查看范围
     */
    public static final ForgeConfigSpec.IntValue REWARD_VIEW_PAST_RANGE;
    /**
     * 未来签到奖励查看范围
     */
    public static final ForgeConfigSpec.IntValue REWARD_VIEW_FUTURE_RANGE;

    // endregion 基础配置


    // region 权限相关

    public static final ForgeConfigSpec.IntValue PERMISSION_VIRTUAL_OP;
    public static final ForgeConfigSpec.IntValue PERMISSION_SIGN;
    public static final ForgeConfigSpec.IntValue PERMISSION_SIGNEX;
    public static final ForgeConfigSpec.IntValue PERMISSION_REWARD;
    public static final ForgeConfigSpec.IntValue PERMISSION_CDK;
    public static final ForgeConfigSpec.IntValue PERMISSION_CARD;
    public static final ForgeConfigSpec.IntValue PERMISSION_CARD_GET;
    public static final ForgeConfigSpec.IntValue PERMISSION_CARD_SET;

    /**
     * 编辑奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_EDIT_REWARD;
    /**
     * 查看基础奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_BASE_REWARD;
    /**
     * 查看连续奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CONTINUOUS_REWARD;
    /**
     * 查看循环奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CYCLE_REWARD;
    /**
     * 查看年度奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_YEAR_REWARD;
    /**
     * 查看月度奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_MONTH_REWARD;
    /**
     * 查看周度奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_WEEK_REWARD;
    /**
     * 查看具体时间奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DATE_TIME_REWARD;
    /**
     * 查看累计奖励配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CUMULATIVE_REWARD;
    /**
     * 查看随机奖励池配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_RANDOM_REWARD;
    /**
     * 查看兑换码奖励池配置所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CDK_REWARD;
    /**
     * 客户端显示奖励概率所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_REWARD_PROBABILITY;
    /**
     * 客户端显示奖励详情所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_REWARD_DETAIL;
    /**
     * 显示领取失败的奖励提示所需的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_REWARD_FAILED_TIPS;
    /**
     * 指令奖励执行的权限
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_COMMAND_REWARD;

    // endregion 相关权限

    static {
        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

        // 基础配置
        {
            // 补签
            {
                SERVER_BUILDER.comment("Make-up Sign-In", "补签").push("makeUp");
                // 补签卡(不是签到卡哦)
                SIGN_IN_CARD = SERVER_BUILDER
                        .comment("Allow players to use a Sign-in Card for missed sign-ins? (SIGN_IN_CARD not a sign in card, it's a Make-up Sign-in Card.)"
                                , "To obtain a Sign-in Card, you can add a reward of type SIGN_IN_CARD to the sign-in rewards."
                                , "是否允许玩家使用补签卡进行补签。(不是签到卡哦)"
                                , "可以在签到奖励里面添加类型为SIGN_IN_CARD的奖励来获得补签卡。")
                        .define("signInCard", true);

                // 最大补签天数
                RE_SIGN_IN_DAYS = SERVER_BUILDER
                        .comment("How many days can the Sign-in Card be renewed for."
                                , "补签卡最远可补签多少天以前的漏签。")
                        .defineInRange("reSignInDays", 30, 1, 365);

                // 补签仅基础奖励
                SIGN_IN_CARD_ONLY_BASE_REWARD = SERVER_BUILDER
                        .comment("Whether the player only gets the base rewards when using the Sign-in Card."
                                , "使用补签卡进行补签时是否仅获得基础奖励。")
                        .define("signInCardOnlyBaseReward", true);
                SERVER_BUILDER.pop();
            }

            // 签到冷却
            {
                SERVER_BUILDER.comment("Sign-In cooling", "签到冷却").push("cooling");
                // 签到时间冷却方式
                TIME_COOLING_METHOD = SERVER_BUILDER
                        .comment("Sign in time cooling method. FIXED_TIME: Fixed time point, FIXED_INTERVAL: Fixed time interval."
                                , "签到时间冷却方式。 FIXED_TIME: 固定时间， FIXED_INTERVAL: 固定时间间隔， MIXED: 混合模式。")
                        .define("timeCoolingMethod", EnumTimeCoolingMethod.FIXED_TIME.name());

                // 签到冷却刷新时间
                TIME_COOLING_TIME = SERVER_BUILDER
                        .comment("Sign in time cooldown time, expressed as a decimal, with the integer part in hours and the decimal part in minutes."
                                , "If timeCoolingMethod=FIXED_TIME(default), it means that the sign-in cooldown is refreshed at 4.00(default) every day."
                                , "If timeCoolingMethod=MIXED, it means that the sign-in cooldown is refreshed at 4.00 (default) every day, and it will take 12 hours and 34 minutes (default) since the last sign-in before it can be signed in again."
                                , "签到时间冷却时间，以小数表示时间，整数部分为小时，小数部分为分钟。"
                                , "若timeCoolingMethod=FIXED_TIME(默认)，则表示每天4.00(默认)刷新签到冷却；"
                                , "若timeCoolingMethod=MIXED，则表示每天4.00(默认)刷新签到冷却，并且需要距离上次签到12小时34分钟(默认)后才能再次签到。")
                        .defineInRange("timeCoolingTime", 0.00, -23.59, 23.59);

                // 签到冷却刷新间隔
                TIME_COOLING_INTERVAL = SERVER_BUILDER
                        .comment("Sign in time cooldown time, expressed as a decimal, with the integer part in hours and the decimal part in minutes."
                                , "If timeCoolingMethod=FIXED_INTERVAL, it means that the player refreshed the sign-in cooldown 12 hours and 34 minutes(default) after the last sign-in;"
                                , "If timeCoolingMethod=MIXED, it means that the sign-in cooldown is refreshed at 4.00 (default) every day, and it will take 12 hours and 34 minutes (default) since the last sign-in before it can be signed in again."
                                , "签到时间冷却时间，以小数表示时间，整数部分为小时，小数部分为分钟。"
                                , "若timeCoolingMethod=FIXED_INTERVAL，则表示玩家在上次签到12小时34分钟(默认)后刷新签到冷却；"
                                , "若timeCoolingMethod=MIXED，则表示每天4.00(默认)刷新签到冷却，并且需要距离上次签到12小时34分钟(默认)后才能再次签到。")
                        .defineInRange("timeCoolingInterval", 12.34, 0.00, 23.59);
                SERVER_BUILDER.pop();
            }

            // 服务器时间
            {
                SERVER_BUILDER.comment("Server time", "服务器时间").push("dateTime");
                // 服务器时间
                SERVER_TIME = SERVER_BUILDER
                        .comment("Calculate the server time offset by matching the original time with the actual time to calibrate the server time."
                                , "服务器原时间，与 实际时间 配合计算服务器时间偏移以校准服务器时间。")
                        .define("serverTime", DateUtils.toDateTimeString(new Date()));

                // 实际时间
                ACTUAL_TIME = SERVER_BUILDER
                        .comment("Calculate the server time offset by matching the original time with the actual time to calibrate the server time."
                                , "实际时间，与 服务器原时间 配合计算服务器时间偏移以校准服务器时间。")
                        .define("serverCalibrationTime", DateUtils.toDateTimeString(new Date()));
                SERVER_BUILDER.pop();

                SERVER_BUILDER.comment("Reward-related", "奖励相关").push("reward");
                // 奖励领取是否受玩家幸运/霉运影响
                REWARD_AFFECTED_BY_LUCK = SERVER_BUILDER
                        .comment("Whether the rewards will be affected by the player's luck/unluck."
                                , "奖励领取是否受玩家幸运/霉运影响。")
                        .define("rewardAffectedByLuck", true);

                // 连续签到奖励 天数达标后是否允许一直领取该标准奖励
                CONTINUOUS_REWARDS_REPEATABLE = SERVER_BUILDER
                        .comment("Whether the Continuous-Rewards can be repeatedly obtained after reaching the standard."
                                , "连续签到奖励 天数达标后是否允许一直领取该标准奖励。")
                        .define("continuousRewardsRepeatable", false);

                // 签到周期奖励 天数达标后是否允许一直领取该标准奖励
                CYCLE_REWARDS_REPEATABLE = SERVER_BUILDER
                        .comment("Whether the Cycle-Rewards can be repeatedly obtained after reaching the standard."
                                , "签到周期奖励 天数达标后是否允许一直领取该标准奖励。")
                        .define("cycleRewardsRepeatable", false);
                SERVER_BUILDER.pop();
            }

            // 其他
            {
                SERVER_BUILDER.comment("etc", "杂项").push("etc");
                // 自动签到
                AUTO_SIGN_IN = SERVER_BUILDER
                        .comment("Players automatically sign in when they enter the server."
                                , "是否允许玩家在进入服务器时自动签到。")
                        .define("autoSignIn", true);

                // 玩家签到数据同步网络包大小
                PLAYER_DATA_SYNC_PACKET_SIZE = SERVER_BUILDER
                        .comment("The maximum size of the player data synchronization network packet."
                                , "When the amount of player sign-in data is too large, "
                                , "causing the player to enter the server with an error message 'Invalid Player Data', "
                                , "please reduce this value."
                                , "玩家数据同步网络包的大小。当玩家签到数据量过大，导致玩家进入服务器报错『无效的玩家数据』时请将此值改小。")
                        .defineInRange("playerDataSyncPacketSize", 100, 1, 1024);

                // 帮助指令信息头部内容
                HELP_HEADER = SERVER_BUILDER
                        .comment("The header content of the help command.",
                                "帮助指令信息头部内容。")
                        .define("helpHeader", "-----==== Sakura Sign-In Help (%d/%d) ====-----");

                // 帮助信息每页显示的数量
                HELP_INFO_NUM_PER_PAGE = SERVER_BUILDER
                        .comment("The number of help information displayed per page.",
                                "每页显示的帮助信息数量。")
                        .defineInRange("helpInfoNumPerPage", 5, 1, 9999);

                // 服务器默认语言
                DEFAULT_LANGUAGE = SERVER_BUILDER
                        .comment("The default language of the server."
                                , "服务器默认语言。")
                        .define("defaultLanguage", "en_us");

                // 历史签到奖励查看范围
                REWARD_VIEW_PAST_RANGE = SERVER_BUILDER
                        .comment("The range of history sign-in rewards viewed by the player."
                                , "玩家查看历史签到奖励的范围（天）。")
                        .defineInRange("rewardViewPastRange", 15, 0, Integer.MAX_VALUE);

                // 未来签到奖励查看范围
                REWARD_VIEW_FUTURE_RANGE = SERVER_BUILDER
                        .comment("The range of future sign-in rewards viewed by the player."
                                , "玩家查看未来签到奖励的范围（天）。")
                        .defineInRange("rewardViewFutureRange", 15, 0, Integer.MAX_VALUE);

                SERVER_BUILDER.pop();
            }
        }

        // 权限
        {
            SERVER_BUILDER.comment("Permission", "权限相关").push("permission");

            // 指令权限
            {
                SERVER_BUILDER.comment("Command Permission", "指令权限").push("command");

                PERMISSION_VIRTUAL_OP = SERVER_BUILDER
                        .comment("The permission level required to use the 'Set virtual permission' command, and also used as the permission level for modifying server configuration."
                                , "设置虚拟权限指令所需的权限等级，同时用于控制使用'修改服务器配置指令'的权限。")
                        .defineInRange("permissionVirtualOp", 4, 0, 4);

                PERMISSION_SIGN = SERVER_BUILDER
                        .comment("The permission level required to use the 'Sign in' command."
                                , "签到指令所需的权限等级。")
                        .defineInRange("permissionSign", 0, 0, 4);

                PERMISSION_SIGNEX = SERVER_BUILDER
                        .comment("The permission level required to use the 'Sign in and Reward' command."
                                , "签到并领取奖励所需的权限等级。")
                        .defineInRange("permissionSignEx", 0, 0, 4);

                PERMISSION_REWARD = SERVER_BUILDER
                        .comment("The permission level required to use the 'Reward' command."
                                , "奖励指令所需的权限等级。")
                        .defineInRange("permissionReward", 0, 0, 4);

                PERMISSION_CDK = SERVER_BUILDER
                        .comment("The permission level required to use the 'CDK Reward' command."
                                , "CDK奖励指令所需的权限等级。")
                        .defineInRange("permissionCdk", 0, 0, 4);

                PERMISSION_CARD = SERVER_BUILDER
                        .comment("The permission level required to use the 'Get Sign-In Card' command."
                                , "查询签到卡指令所需的权限等级。")
                        .defineInRange("permissionCard", 0, 0, 4);

                PERMISSION_CARD_GET = SERVER_BUILDER
                        .comment("The permission level required to use the 'Get another player's Sign-In Card' command."
                                , "获取其他玩家签到卡指令所需的权限等级。")
                        .defineInRange("permissionCardGet", 2, 0, 4);

                PERMISSION_CARD_SET = SERVER_BUILDER
                        .comment("The permission level required to use the 'Set Sign-In Card' command."
                                , "设置签到卡指令所需的权限等级。")
                        .defineInRange("permissionCardSet", 2, 0, 4);


                SERVER_BUILDER.pop();
            }

            // 奖励相关权限
            {
                SERVER_BUILDER.comment("Reward"
                        , "If a player does not have permission to view the configuration of a specific reward rule (such as the basic reward) "
                        , "but has permission to edit the reward configuration, then when the player uploads the reward configuration, "
                        , "the server will ignore the configuration of that reward rule and retain the original server-side configuration without overwriting it."
                        , "奖励相关"
                        , "如果玩家没有某个奖励规则（例如基础奖励）的配置查看权限，但拥有编辑奖励配置的权限，"
                        , "则当该玩家上传奖励配置时，服务器将忽略该奖励规则的配置，并保留服务器原有的配置，不会被覆盖。").push("reward");

                // 编辑奖励配置所需的权限
                PERMISSION_EDIT_REWARD = SERVER_BUILDER
                        .comment("The permission level required to edit the reward configuration."
                                , "编辑奖励配置所需的权限。")
                        .defineInRange("permissionEditReward", 3, 0, 4);

                // 查看基础奖励配置所需的权限
                PERMISSION_BASE_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the base reward configuration."
                                , "查看基础奖励配置所需的权限。")
                        .defineInRange("permissionBaseReward", 0, 0, 4);

                // 查看连续奖励配置所需的权限
                PERMISSION_CONTINUOUS_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the continuous reward configuration."
                                , "查看连续奖励配置所需的权限。")
                        .defineInRange("permissionContinuousReward", 0, 0, 4);

                // 查看循环奖励配置所需的权限
                PERMISSION_CYCLE_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the cycle reward configuration."
                                , "查看循环奖励配置所需的权限。")
                        .defineInRange("permissionCycleReward", 0, 0, 4);

                // 查看年度奖励配置所需的权限
                PERMISSION_YEAR_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the year reward configuration."
                                , "查看年度奖励配置所需的权限。")
                        .defineInRange("permissionYearReward", 0, 0, 4);

                // 查看月度奖励配置所需的权限
                PERMISSION_MONTH_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the month reward configuration."
                                , "查看月度奖励配置所需的权限。")
                        .defineInRange("permissionMonthReward", 0, 0, 4);

                // 查看周度奖励配置所需的权限
                PERMISSION_WEEK_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the week reward configuration."
                                , "查看周度奖励配置所需的权限。")
                        .defineInRange("permissionWeekReward", 0, 0, 4);

                // 查看具体时间奖励配置所需的权限
                PERMISSION_DATE_TIME_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the date-time reward configuration."
                                , "查看具体时间奖励配置所需的权限。")
                        .defineInRange("permissionDateTimeReward", 0, 0, 4);

                // 查看累计奖励配置所需的权限
                PERMISSION_CUMULATIVE_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the cumulative reward configuration."
                                , "查看累计奖励配置所需的权限。")
                        .defineInRange("permissionCumulativeReward", 0, 0, 4);

                // 查看随机奖励池配置所需的权限
                PERMISSION_RANDOM_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the random reward configuration."
                                , "查看随机奖励池配置所需的权限。")
                        .defineInRange("permissionRandomReward", 0, 0, 4);

                // 查看兑换码奖励池配置所需的权限
                PERMISSION_CDK_REWARD = SERVER_BUILDER
                        .comment("The permission level required to view the cdk reward configuration."
                                , "查看兑换码奖励池配置所需的权限。")
                        .defineInRange("permissionCdkReward", 3, 0, 4);

                // 客户端显示奖励概率所需的权限
                PERMISSION_REWARD_PROBABILITY = SERVER_BUILDER
                        .comment("The permission level required to view the reward probability on the client."
                                , "客户端显示奖励概率所需的权限。")
                        .defineInRange("permissionRewardProbability", 0, 0, 4);

                // 客户端显示奖励详情所需的权限
                PERMISSION_REWARD_DETAIL = SERVER_BUILDER
                        .comment("The permission level required to view the reward detail on the client."
                                , "客户端显示奖励详情所需的权限，即使玩家拥有显示奖励的权限。")
                        .defineInRange("permissionRewardDetail", 0, 0, 4);

                // 显示领取失败的奖励提示所需的权限
                PERMISSION_REWARD_FAILED_TIPS = SERVER_BUILDER
                        .comment("The permission level required to view the reward failed tips on the client."
                                , "显示领取失败的奖励提示所需的权限。")
                        .defineInRange("permissionRewardFailedTips", 0, 0, 4);

                // 指令类型奖励执行时使用的权限等级
                PERMISSION_COMMAND_REWARD = SERVER_BUILDER
                        .comment("The permission level to execute the command type reward."
                                , "指令类型奖励执行时使用的权限等级。")
                        .defineInRange("permissionCommandReward", 2, 0, 4);

                SERVER_BUILDER.pop();
            }

            SERVER_BUILDER.pop();
        }

        SERVER_CONFIG = SERVER_BUILDER.build();
    }

}
