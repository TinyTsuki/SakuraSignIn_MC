package xin.vanilla.mc.util;

import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.Map;

public class I18nUtils {
    private static final Map<String, String> ZH_CN_KEY_MAP = new HashMap<>() {{
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
        put("签到周期奖励", "button.sakura_sign_in.reward_cycle");
        put("年度签到奖励", "button.sakura_sign_in.reward_year");
        put("月度签到奖励", "button.sakura_sign_in.reward_month");
        put("周度签到奖励", "button.sakura_sign_in.reward_week");
        put("具体时间奖励", "button.sakura_sign_in.reward_time");
        put("累计签到奖励", "button.sakura_sign_in.reward_cumulative");
        put("编辑", "option.sakura_sign_in.edit");
        put("复制", "option.sakura_sign_in.copy");
        put("剪切", "option.sakura_sign_in.cut");
        put("粘贴", "option.sakura_sign_in.paste");
        put("删除", "option.sakura_sign_in.delete");
        put("清空", "option.sakura_sign_in.clear");
        put("取消", "option.sakura_sign_in.cancel");
        put("提交", "option.sakura_sign_in.submit");
        put("确认", "option.sakura_sign_in.confirm");
        put("不再提醒", "option.sakura_sign_in.no_remind");
        put("请输入", "tips.sakura_sign_in.enter_something");
        put("请输入规则名称", "tips.sakura_sign_in.enter_reward_rule_key");
        put("请输入物品Json", "tips.sakura_sign_in.enter_item_json");
        put("请输入物品数量", "tips.sakura_sign_in.enter_item_count");
        put("请输入物品NBT", "tips.sakura_sign_in.enter_item_nbt");
        put("请输入效果Json", "tips.sakura_sign_in.enter_effect_json");
        put("请输入持续时间", "tips.sakura_sign_in.enter_effect_duration");
        put("请输入效果等级", "tips.sakura_sign_in.enter_effect_amplifier");
        put("请输入经验点值", "tips.sakura_sign_in.enter_exp_point");
        put("请输入经验等级", "tips.sakura_sign_in.enter_exp_level");
        put("请输入补签卡数量", "tips.sakura_sign_in.enter_sign_in_card");
        put("请输入进度Json", "tips.sakura_sign_in.enter_advancement_json");
        put("请输入消息", "tips.sakura_sign_in.enter_message");
        put("请输入指令", "tips.sakura_sign_in.enter_command");
        put("规则名称[%s]输入有误", "tips.sakura_sign_in.reward_rule_s_error");
        put("物品Json[%s]输入有误", "tips.sakura_sign_in.item_json_s_error");
        put("物品数量[%s]输入有误", "tips.sakura_sign_in.item_count_s_error");
        put("物品NBT[%s]输入有误", "tips.sakura_sign_in.item_nbt_s_error");
        put("效果Json[%s]输入有误", "tips.sakura_sign_in.effect_json_s_error");
        put("持续时间[%s]输入有误", "tips.sakura_sign_in.effect_duration_s_error");
        put("效果等级[%s]输入有误", "tips.sakura_sign_in.effect_amplifier_s_error");
        put("进度Json[%s]输入有误", "tips.sakura_sign_in.advancement_json_s_error");
        put("输入值[%s]有误", "tips.sakura_sign_in.enter_value_s_error");
        put("展开侧边栏", "tips.sakura_sign_in.open_sidebar");
        put("收起侧边栏", "tips.sakura_sign_in.close_sidebar");
        put("Y轴偏移:\n%.1f\n点击重置", "tips.sakura_sign_in.y_offset");
        put("Ctrl + 鼠标右键确认", "tips.sakura_sign_in.cancel_or_confirm");
        put("列出模式\n物品栏 (%s)", "tips.sakura_sign_in.item_select_list_inventory_mode");
        put("列出模式\n所有物品 (%s)", "tips.sakura_sign_in.item_select_list_all_mode");
        put("列出模式\n所有效果 (%s)", "tips.sakura_sign_in.effect_select_list_all_mode");
        put("列出模式\n玩家拥有 (%s)", "tips.sakura_sign_in.effect_select_list_player_mode");
        put("列出模式\n所有进度 (%s)", "tips.sakura_sign_in.advancement_select_list_all_mode");
        put("列出模式\n有图标的 (%s)", "tips.sakura_sign_in.advancement_select_list_icon_mode");
        put("设置数量\n当前 %s", "tips.sakura_sign_in.set_count_s");
        put("设置持续时间\n当前 %s", "tips.sakura_sign_in.set_duration_s");
        put("设置效果等级\n当前 %s", "tips.sakura_sign_in.set_amplifier_s");
        put("编辑NBT", "tips.sakura_sign_in.edit_nbt");
        put("页面上部分元素\n按住Shift键可查看帮助信息", "tips.sakura_sign_in.help_button");
        put("比如红色字体按钮, 按住Shift时会给予帮助信息:\n按住Control键 并且 鼠标右键点击以确认\n直接点击是取消哦", "tips.sakura_sign_in.help_button_shift");
        put("从服务器同步配置文件", "tips.sakura_sign_in.download_reward_config");
        put("将配置文件同步至服务器", "tips.sakura_sign_in.upload_reward_config");
        put("将配置文件同步至服务器\n权限不足", "tips.sakura_sign_in.upload_reward_config_no_permission");
        put("打开配置文件夹", "tips.sakura_sign_in.open_config_folder");
        put("奖励规则排序", "tips.sakura_sign_in.reward_rule_sort");
        put("使用键盘%s键也可以哦", "tips.sakura_sign_in.use_s_key");
        put("点击切换主题", "tips.sakura_sign_in.click_to_change_theme");
        put("左键点击切换主题\n右键点击选择外部主题", "tips.sakura_sign_in.click_to_change_theme_or_select_external_theme");
        put("按住Ctrl或Alt键可拖动按钮\nCtrl: 绝对位置坐标\nAlt: 屏幕百分比位置", "tips.sakura_sign_in.drag_inventory_button");
        put("鼠标左键签到\n右键补签/领取奖励", "tips.sakura_sign_in.how_to_sign_in");
        put("补签卡: %s\n连续签到: %sd\n累计签到: %sd", "tips.sakura_sign_in.sign_in_info");
        put("签到页面开屏提示", "tips.sakura_sign_in.sign_in_screen_tips");
        put("奖励配置页面开屏提示", "tips.sakura_sign_in.reward_option_screen_tips");
        put("已选择主题文件: %s", "message.sakura_sign_in.selected_theme_file_s");
        put("前面的的日期以后再来探索吧。", "message.sakura_sign_in.next_day_cannot_operate");
        put("已经签过到了哦。", "message.sakura_sign_in.already_signed");
        put("不论怎么点也不会获取俩次奖励吧。", "message.sakura_sign_in.already_get_reward");
        put("服务器补签功能被禁用了哦。", "message.sakura_sign_in.server_not_enable_sign_in_card");
        put("补签卡不足了哦。", "message.sakura_sign_in.not_enough_sign_in_card");
        put("过去的的日期怎么想也回不去了吧。", "message.sakura_sign_in.past_day_cannot_operate");

        put("SakuraSignIn server is offline!", "message.sakura_sign_in.sakurasignin_server_is_offline");
        put("当前拥有%d张补签卡", "message.sakura_sign_in.has_sign_in_card_d");
        put("给予%d张补签卡", "message.sakura_sign_in.give_sign_in_card_d");
        put("获得%d张补签卡", "message.sakura_sign_in.get_sign_in_card_d");
        put("补签卡被设置为了%d张", "message.sakura_sign_in.set_sign_in_card_d");
        put("玩家[%s]拥有%d张补签卡", "message.sakura_sign_in.set_player_s_sign_in_card_d");
        put("服务器已启用自动签到", "message.sakura_sign_in.server_enabled_auto_sign");
        put("服务器已禁用自动签到", "message.sakura_sign_in.server_disabled_auto_sign");
        put("服务器签到时间冷却方式为: %s", "message.sakura_sign_in.sign_in_time_cool_down_mode_s");
        put("服务器签到冷却刷新时间为: %05.2f", "message.sakura_sign_in.sign_in_time_cool_down_refresh_time_f");
        put("服务器签到冷却刷新间隔为: %05.2f", "message.sakura_sign_in.sign_in_time_cool_down_refresh_interval_f");
        put("服务器已启用补签卡", "message.sakura_sign_in.server_enabled_sign_in_card");
        put("服务器已禁用补签卡", "message.sakura_sign_in.server_disabled_sign_in_card");
        put("服务器最大补签天数为: %d", "message.sakura_sign_in.max_sign_in_day_d");
        put("服务器已启用补签仅获得基础奖励", "message.sakura_sign_in.server_enabled_sign_in_card_only_basic_reward");
        put("服务器已禁用补签仅获得基础奖励", "message.sakura_sign_in.server_disabled_sign_in_card_only_basic_reward");
        put("服务器当前时间: %s", "message.sakura_sign_in.server_current_time_s");
        put("玩家签到数据同步网络包大小为: %d", "message.sakura_sign_in.player_data_sync_packet_size_d");

        put("服务器时间已设置为: %s", "message.sakura_sign_in.set_server_time_s");
        put("服务器最大补签天数已被设置为: %d", "message.sakura_sign_in.set_max_sign_in_day_d");
        put("服务器签到时间冷却方式已被设置为: %s", "message.sakura_sign_in.set_sign_in_time_cool_down_mode_s");
        put("服务器签到冷却刷新时间已被设置为: %05.2f", "message.sakura_sign_in.set_sign_in_time_cool_down_refresh_time_f");
        put("服务器签到冷却刷新间隔已被设置为: %05.2f", "message.sakura_sign_in.set_sign_in_time_cool_down_refresh_interval_f");
        put("玩家签到数据同步网络包大小已被设置为: %d", "message.sakura_sign_in.set_player_data_sync_packet_size_d");

        put("要到今天的%05.2f后才能签到哦", "message.sakura_sign_in.next_sign_in_time_f");
        put("签到日期晚于服务器当前日期，签到失败", "message.sakura_sign_in.sign_in_date_late_server_current_date_fail");
        put("签到日期早于服务器当前日期，签到失败", "message.sakura_sign_in.sign_in_date_early_server_current_date_fail");
        put("补签日期需早于服务器当前日期，补签失败", "message.sakura_sign_in.compensate_date_not_early_server_current_date_fail");
        put("签到冷却中，签到失败，请稍后再试", "message.sakura_sign_in.sign_in_cool_down_fail");
        put("服务器补签功能被禁用了哦，补签失败", "message.sakura_sign_in.server_not_enable_sign_in_card_fail");
        put("补签卡不足，补签失败", "message.sakura_sign_in.not_enough_sign_in_card_fail");
        put("%s的奖励已经领取过啦", "message.sakura_sign_in.already_receive_reward_s");
        put("没有查询到[%s]的签到记录哦，鉴定为阁下没有签到！", "message.sakura_sign_in.not_sign_in");
        put("奖励领取成功", "message.sakura_sign_in.receive_reward_success");
        put("签到成功, %s/%s", "message.sakura_sign_in.sign_in_success_s");

    }};

    public static String getI18nKey(String key) {
        return ZH_CN_KEY_MAP.getOrDefault(key, key);
    }

    public static String get(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static String getByZh(String key, Object... args) {
        try {
            return I18n.get(ZH_CN_KEY_MAP.get(key), args);
        } catch (Exception e) {
            return String.format(key, args);
        }
    }
}
