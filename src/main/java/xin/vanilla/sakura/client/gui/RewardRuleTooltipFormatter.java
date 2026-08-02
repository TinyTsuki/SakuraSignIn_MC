package xin.vanilla.sakura.client.gui;

import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.enums.ERewardRule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将奖励组的紧凑规则键转换为可读的 Shift 悬浮说明。 */
public final class RewardRuleTooltipFormatter {
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(\\d{4})(?:~(\\d+))?[-/](\\d{1,2})(?:~(\\d+))?[-/](\\d{1,2})(?:~(\\d+))?(?:[T ](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?)?");

    private RewardRuleTooltipFormatter() {
    }

    public static String describe(ERewardRule rule, String key, String title) {
        if (rule == null) return title;
        switch (rule) {
            case BASE_REWARD:
                return word("reward_group_hint_base");
            case CONTINUOUS_REWARD:
                return format("reward_group_hint_continuous_s", key);
            case CYCLE_REWARD:
                return format("reward_group_hint_cycle_s", key);
            case YEAR_REWARD:
                return format("reward_group_hint_year_s", key);
            case MONTH_REWARD:
                return format("reward_group_hint_month_s", key);
            case WEEK_REWARD:
                return format("reward_group_hint_week_s", title);
            case DATE_TIME_REWARD:
                return describeDateTime(key);
            case CUMULATIVE_REWARD:
                return format("reward_group_hint_cumulative_s", key);
            case RANDOM_REWARD:
                return format("reward_group_hint_random_s", title);
            case CDK_REWARD:
                return format("reward_group_hint_cdk_s", title);
            case PERSONAL_DATE_REWARD:
                return format("reward_group_hint_personal_s", title);
            case LOTTERY_REWARD:
                return format("reward_group_hint_lottery_s", title);
            default:
                return title;
        }
    }

    static String describeDateTime(String key) {
        Matcher matcher = DATE_RANGE.matcher(key == null ? "" : key.trim());
        if (!matcher.matches()) {
            return format("reward_group_hint_raw_s", key);
        }
        String start = describeStart(matcher);
        int years = number(matcher.group(2));
        int months = number(matcher.group(4));
        int days = number(matcher.group(6));
        if (years == 0 && months == 0 && days == 0) {
            return format("reward_group_hint_date_exact_s", start);
        }
        if (years == 0 && months == 0) {
            return format("reward_group_hint_date_days_ss", start, days + 1);
        }
        return format("reward_group_hint_date_ranges_ssss", start,
                years, months, days);
    }

    private static String describeStart(Matcher matcher) {
        String year = matcher.group(1);
        int month = number(matcher.group(3));
        int day = number(matcher.group(5));
        String date;
        if (month == 0 && day == 0) {
            date = word("reward_group_date_daily");
        } else if ("0000".equals(year) && month > 0 && day == 0) {
            date = format("reward_group_date_annual_month_s", month);
        } else if ("0000".equals(year) && month > 0) {
            date = format("reward_group_date_annual_ss", month, day);
        } else if (month == 0 && day > 0) {
            date = format("reward_group_date_monthly_s", day);
        } else if (day == 0) {
            date = format("reward_group_date_month_ss", year, month);
        } else {
            date = format("reward_group_date_fixed_sss", year, month, day);
        }
        if (matcher.group(7) != null && matcher.group(8) != null) {
            return format("reward_group_date_time_sss", date,
                    twoDigits(matcher.group(7)), twoDigits(matcher.group(8))
                            + (matcher.group(9) == null ? "" : ":" + twoDigits(matcher.group(9))));
        }
        return date;
    }

    private static int number(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String twoDigits(String value) {
        int number = number(value);
        return number < 10 ? "0" + number : String.valueOf(number);
    }

    private static String word(String key) {
        return SakuraComponent.get().translateClient("word", key);
    }

    private static String format(String key, Object... args) {
        return SakuraComponent.get().translateClient("format", key, args);
    }
}
