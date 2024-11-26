package xin.vanilla.mc.util;

import net.minecraft.client.resources.I18n;

import java.util.HashMap;
import java.util.Map;

public class I18nUtils {
    private static final Map<String, String> ZH_CN_KEY_MAP = new HashMap<String, String>() {{
        put("奖励规则类型", "title.sakura_sign_in.reward_rule_type");
        put("基础奖励", "title.sakura_sign_in.base_reward");
        put("第%s天", "title.sakura_sign_in.day_s");
        put("年度第%s天", "title.sakura_sign_in.year_day_s");
        put("月度第%s天", "title.sakura_sign_in.month_day_s");
        put("周1", "title.sakura_sign_in.week_1");
        put("周2", "title.sakura_sign_in.week_2");
        put("周3", "title.sakura_sign_in.week_3");
        put("周4", "title.sakura_sign_in.week_4");
        put("周5", "title.sakura_sign_in.week_5");
        put("周6", "title.sakura_sign_in.week_6");
        put("周7", "title.sakura_sign_in.week_7");
        put("签到基础奖励", "button.sakura_sign_in.reward_base");
        put("连续签到奖励", "button.sakura_sign_in.reward_continuous");
        put("连续签到周期奖励", "button.sakura_sign_in.reward_cycle");
        put("年度签到奖励", "button.sakura_sign_in.reward_year");
        put("月度签到奖励", "button.sakura_sign_in.reward_month");
        put("周度签到奖励", "button.sakura_sign_in.reward_week");
        put("具体时间签到奖励", "button.sakura_sign_in.reward_time");
        put("修改", "option.sakura_sign_in.edit");
        put("复制", "option.sakura_sign_in.copy");
        put("删除", "option.sakura_sign_in.delete");
        put("清空", "option.sakura_sign_in.clear");
        put("展开侧边栏", "tips.sakura_sign_in.open_sidebar");
        put("收起侧边栏", "tips.sakura_sign_in.close_sidebar");
        put("Y轴偏移:\n%.1f\n点击重置", "tips.sakura_sign_in.y_offset");
        put("左键取消\n右键确认", "tips.sakura_sign_in.cancel_or_confirm");
        put("是否清空当前奖励规则类型下所有配置\n        左键取消     右键确认", "tips.sakura_sign_in.clear_all_config");

    }};

    public static String get(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static String getByZh(String key, Object... args) {
        try {
            return I18n.get(ZH_CN_KEY_MAP.get(key), args);
        } catch (Exception e) {
            return key;
        }
    }
}