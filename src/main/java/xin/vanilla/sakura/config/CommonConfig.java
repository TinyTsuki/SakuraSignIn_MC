package xin.vanilla.sakura.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigData;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigScope;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.common.config.annotation.ConfigEntry;
import xin.vanilla.banira.common.network.packet.ConfigSnapshotToClient;
import xin.vanilla.banira.common.network.packet.ConfigSyncToServer;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.access.CommonConfigAccess;
import xin.vanilla.sakura.domain.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端与通用设置共用一份 Banira COMMON 配置。
 */
@Config(name = SakuraSignIn.MODID + "-common", type = ConfigScope.COMMON)
public class CommonConfig implements ConfigData {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "签到与补签", en_us = "Sign-in and make-up sign-in")
    private MakeUpCategory makeUp = new MakeUpCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "签到冷却", en_us = "Sign-in cooldown")
    private CoolingCategory cooling = new CoolingCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "服务器时间校准", en_us = "Server time calibration")
    private DateTimeCategory dateTime = new DateTimeCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "奖励规则", en_us = "Reward rules")
    private RewardCategory reward = new RewardCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "服务端运行设置", en_us = "Server runtime settings")
    private ServerCategory server = new ServerCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "签到历史详情保留", en_us = "Sign-in history retention")
    private HistoryCategory history = new HistoryCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "指令名称，请勿添加 /", en_us = "Command names without /")
    private CommandCategory command = new CommandCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "无前缀简短指令", en_us = "Commands without the mod prefix")
    private ConciseCategory concise = new ConciseCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "指令与奖励编辑权限", en_us = "Command and reward permissions")
    private PermissionCategory permission = new PermissionCategory();

    public static RootView get() {
        return CommonConfigAccess.root(BaniraConfig.holder(CommonConfig.class));
    }

    public static void save() {
        ConfigHolder holder = BaniraConfig.holder(CommonConfig.class);
        if (holder != null) {
            holder.save();
        }
    }

    /**
     * 多人游戏登录时将服务端 COMMON 配置写入客户端运行时视图。
     */
    public static void syncToPlayer(Object player) {
        ConfigHolder holder = BaniraConfig.holder(CommonConfig.class);
        if (holder == null) {
            return;
        }
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (ConfigEntryDescriptor descriptor : holder.getDescriptors()) {
            Object value = holder.get(descriptor.getPath());
            snapshot.put(
                    descriptor.getPath(),
                    value != null ? ConfigSyncToServer.encodeConfigValue(value) : ""
            );
        }
        BaniraNetwork.sendToPlayer(
                new ConfigSnapshotToClient(holder.getConfigName(), snapshot),
                player
        );
    }

    public interface RootView {
        MakeUpView makeUp();
        CoolingView cooling();
        DateTimeView dateTime();
        RewardView reward();
        ServerView server();
        HistoryView history();
        CommandView command();
        ConciseView concise();
        PermissionView permission();
        ConfigHolder holder();
    }

    public interface MakeUpView {
        boolean signInCard();
        MakeUpView signInCard(boolean value);
        int reSignInDays();
        MakeUpView reSignInDays(int value);
        boolean signInCardOnlyBaseReward();
        MakeUpView signInCardOnlyBaseReward(boolean value);
    }

    public interface CoolingView {
        ETimeCoolingMethod timeCoolingMethod();
        CoolingView timeCoolingMethod(ETimeCoolingMethod value);
        double timeCoolingTime();
        CoolingView timeCoolingTime(double value);
        double timeCoolingInterval();
        CoolingView timeCoolingInterval(double value);
    }

    public interface DateTimeView {
        String serverTime();
        DateTimeView serverTime(String value);
        String serverCalibrationTime();
        DateTimeView serverCalibrationTime(String value);
    }

    public interface RewardView {
        boolean rewardAffectedByLuck();
        RewardView rewardAffectedByLuck(boolean value);
        boolean continuousRewardsRepeatable();
        RewardView continuousRewardsRepeatable(boolean value);
        boolean cycleRewardsRepeatable();
        RewardView cycleRewardsRepeatable(boolean value);
    }

    public interface ServerView {
        boolean autoSignIn();
        ServerView autoSignIn(boolean value);
        String defaultLanguage();
        ServerView defaultLanguage(String value);
    }

    public interface HistoryView {
        int retentionMonths();
        HistoryView retentionMonths(int value);
        HistoryRetentionPolicy retentionPolicy();
        HistoryView retentionPolicy(HistoryRetentionPolicy value);
    }

    public interface CommandView {
        String commandPrefix();
        CommandView commandPrefix(String value);
        String commandSignIn();
        String commandSignInEx();
        String commandReward();
        String commandCdk();
        String commandCard();
        String commandLanguage();
    }

    public interface ConciseView {
        boolean conciseSignIn();
        boolean conciseSignInEx();
        boolean conciseReward();
        boolean conciseCdk();
        boolean conciseCard();
        boolean conciseLanguage();
    }

    public interface PermissionView {
        int permissionEditReward();
        int permissionBaseReward();
        int permissionContinuousReward();
        int permissionCycleReward();
        int permissionYearReward();
        int permissionMonthReward();
        int permissionWeekReward();
        int permissionDateTimeReward();
        int permissionCumulativeReward();
        int permissionRandomReward();
        int permissionCdkReward();
        int permissionRewardProbability();
        int permissionRewardDetail();
        int permissionRewardFailedTips();
        int permissionCommandReward();
        int permissionServerConfigGet();
        int permissionServerConfigSet();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class MakeUpCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "允许使用补签卡补签", en_us = "Allow make-up sign-in cards")
        private boolean signInCard = true;
        @ConfigEntry.BoundedDiscrete(min = 1, max = 365)
        @ConfigEntry.Gui.Tooltip(zh_cn = "最远可补签的天数", en_us = "Maximum age of a make-up sign-in")
        private int reSignInDays = 30;
        @ConfigEntry.Gui.Tooltip(zh_cn = "补签时仅计算基础与累计奖励", en_us = "Only base and cumulative rewards for make-up sign-in")
        private boolean signInCardOnlyBaseReward = true;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CoolingCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "签到冷却的计算方式", en_us = "How the sign-in cooldown is calculated")
        private ETimeCoolingMethod timeCoolingMethod = ETimeCoolingMethod.FIXED_TIME;
        @ConfigEntry.BoundedDouble(min = -23.59, max = 23.59)
        @ConfigEntry.Gui.Tooltip(zh_cn = "固定时间模式下每日刷新签到的时间", en_us = "Daily sign-in reset time in fixed-time mode")
        private double timeCoolingTime = 0.0;
        @ConfigEntry.BoundedDouble(min = 0.0, max = 23.59)
        @ConfigEntry.Gui.Tooltip(zh_cn = "间隔模式下两次签到之间的小时数", en_us = "Hours required between sign-ins in interval mode")
        private double timeCoolingInterval = 12.34;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class DateTimeCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "服务端时间基准\n通常不需要手动修改", en_us = "Server time baseline\nNormally does not need manual changes")
        private String serverTime = "1970-01-01 00:00:00";
        @ConfigEntry.Gui.Tooltip(zh_cn = "用于修正服务端时间的校准值", en_us = "Calibration value used to adjust server time")
        private String serverCalibrationTime = "1970-01-01 00:00:00";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class RewardCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "奖励概率是否受玩家幸运值影响", en_us = "Whether player luck affects reward probability")
        private boolean rewardAffectedByLuck = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "达到更高连续天数后是否仍可获得较低档奖励", en_us = "Allow lower-tier continuous rewards after reaching higher thresholds")
        private boolean continuousRewardsRepeatable = false;
        @ConfigEntry.Gui.Tooltip(zh_cn = "周期奖励超过最大天数后是否重新循环", en_us = "Restart cycle rewards after the maximum day")
        private boolean cycleRewardsRepeatable = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ServerCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "玩家进入服务器后是否自动尝试签到", en_us = "Automatically attempt sign-in when a player joins")
        private boolean autoSignIn = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "无法确定玩家语言时使用的默认语言", en_us = "Default language when a player's language is unavailable")
        private String defaultLanguage = "en_us";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class HistoryCategory {
        @ConfigEntry.BoundedDiscrete(min = 0, max = 1200)
        @ConfigEntry.Gui.Tooltip(
                zh_cn = "保留最近几个月的签到详情，0 表示永久保留",
                en_us = "Months of detailed history to retain; 0 keeps all details")
        private int retentionMonths = 24;
        @ConfigEntry.Gui.Tooltip(
                zh_cn = "STRIP_REWARD_DETAILS 仅移除奖励快照\nDELETE_MONTH_FILE 删除整个过期月文件",
                en_us = "STRIP_REWARD_DETAILS removes reward snapshots\nDELETE_MONTH_FILE removes expired month files")
        private HistoryRetentionPolicy retentionPolicy = HistoryRetentionPolicy.STRIP_REWARD_DETAILS;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CommandCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "樱花签根指令名称", en_us = "Root command name for Sakura Sign-In")
        private String commandPrefix = "sakura";
        @ConfigEntry.Gui.Tooltip(zh_cn = "签到子指令名称", en_us = "Sign-in subcommand name")
        private String commandSignIn = "sign";
        @ConfigEntry.Gui.Tooltip(zh_cn = "签到并领取奖励子指令名称", en_us = "Sign-in and claim subcommand name")
        private String commandSignInEx = "signex";
        @ConfigEntry.Gui.Tooltip(zh_cn = "领取奖励子指令名称", en_us = "Reward claim subcommand name")
        private String commandReward = "reward";
        @ConfigEntry.Gui.Tooltip(zh_cn = "兑换码子指令名称", en_us = "Redemption-code subcommand name")
        private String commandCdk = "cdk";
        @ConfigEntry.Gui.Tooltip(zh_cn = "补签卡管理子指令名称", en_us = "Make-up card management subcommand name")
        private String commandCard = "card";
        @ConfigEntry.Gui.Tooltip(zh_cn = "语言设置子指令名称", en_us = "Language settings subcommand name")
        private String commandLanguage = "language";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ConciseCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的签到指令", en_us = "Register the sign-in command without the root prefix")
        private boolean conciseSignIn = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的签到并领奖指令", en_us = "Register the sign-and-claim command without the root prefix")
        private boolean conciseSignInEx = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的领奖指令", en_us = "Register the reward command without the root prefix")
        private boolean conciseReward = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的兑换码指令", en_us = "Register the redemption-code command without the root prefix")
        private boolean conciseCdk = true;
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的补签卡指令", en_us = "Register the card command without the root prefix")
        private boolean conciseCard = false;
        @ConfigEntry.Gui.Tooltip(zh_cn = "注册无根指令前缀的语言指令", en_us = "Register the language command without the root prefix")
        private boolean conciseLanguage = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class PermissionCategory {
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "编辑服务端奖励配置所需权限等级", en_us = "Permission level required to edit server reward configuration")
        private int permissionEditReward = 3;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看基础奖励所需权限等级", en_us = "Permission level required to view base rewards")
        private int permissionBaseReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看连续签到奖励所需权限等级", en_us = "Permission level required to view continuous rewards")
        private int permissionContinuousReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看周期奖励所需权限等级", en_us = "Permission level required to view cycle rewards")
        private int permissionCycleReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看年度奖励所需权限等级", en_us = "Permission level required to view annual rewards")
        private int permissionYearReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看月度奖励所需权限等级", en_us = "Permission level required to view monthly rewards")
        private int permissionMonthReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看周度奖励所需权限等级", en_us = "Permission level required to view weekly rewards")
        private int permissionWeekReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看具体时间奖励所需权限等级", en_us = "Permission level required to view date-time rewards")
        private int permissionDateTimeReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看累计奖励所需权限等级", en_us = "Permission level required to view cumulative rewards")
        private int permissionCumulativeReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看随机奖励池所需权限等级", en_us = "Permission level required to view random reward pools")
        private int permissionRandomReward = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看兑换码奖励所需权限等级", en_us = "Permission level required to view redemption-code rewards")
        private int permissionCdkReward = 3;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看奖励概率所需权限等级", en_us = "Permission level required to view reward probabilities")
        private int permissionRewardProbability = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看奖励详情所需权限等级", en_us = "Permission level required to view reward details")
        private int permissionRewardDetail = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看奖励失败原因所需权限等级", en_us = "Permission level required to view reward failure details")
        private int permissionRewardFailedTips = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "使用指令领取奖励所需权限等级", en_us = "Permission level required to claim rewards by command")
        private int permissionCommandReward = 2;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "查看服务端配置所需权限等级", en_us = "Permission level required to view server configuration")
        private int permissionServerConfigGet = 0;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        @ConfigEntry.Gui.Tooltip(zh_cn = "修改服务端配置所需权限等级", en_us = "Permission level required to change server configuration")
        private int permissionServerConfigSet = 3;
    }
}
