package xin.vanilla.sakura.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 主题组件类型
 */
@Getter
public enum EnumThemeComponentType {
    /**
     * 上一年翻页按钮
     */
    PREV_YEAR_BTN(1),
    /**
     * 下一年翻页按钮
     */
    NEXT_YEAR_BTN(1),
    /**
     * 年份文字
     */
    YEAR_TEXT(1),
    /**
     * 上一月翻页按钮
     */
    PREV_MONTH_BTN(1),
    /**
     * 下一月翻页按钮
     */
    NEXT_MONTH_BTN(1),
    /**
     * 月份文字
     */
    MONTH_TEXT(1),
    /**
     * 日期单元格按钮
     */
    DATE_CELL_BTN(1),
    /**
     * 日期单元格文字
     */
    DATE_CELL_TEXT(1),
    /**
     * 奖励详情背景
     */
    TOOLTIP_BG(1),
    /**
     * 奖励详情文字
     */
    TOOLTIP_TEXT(1),
    /**
     * 主题按钮
     */
    THEME_BTN(1),
    /**
     * 展开按钮
     */
    EXPAND_BTN(2),
    /**
     * 折叠按钮
     */
    FOLD_BTN(2),
    /**
     * 帮助按钮
     */
    HELP_BTN(2),
    /**
     * 下载按钮
     */
    DOWNLOAD_BTN(2),
    /**
     * 上传按钮
     */
    UPLOAD_BTN(2),
    /**
     * 打开文件夹按钮
     */
    FOLDER_BTN(2),
    /**
     * 奖励排序按钮
     */
    SORT_BTN(2),
    /**
     * 按键设置按钮
     */
    KEY_BTN(2),
    /**
     * 背景
     */
    BACKGROUND,
    /**
     * 自定义显示内容
     */
    CUSTOM,
    /**
     * 自定义开关，点击后会将值写入参数区，再次点击则移出参数区
     */
    SWITCH,
    ;

    private final int type;

    EnumThemeComponentType() {
        this.type = 0;
    }

    EnumThemeComponentType(int type) {
        this.type = type;
    }

    public static EnumThemeComponentType[] getWithType(int... type) {
        return Arrays.stream(EnumThemeComponentType.values())
                .filter(t -> Arrays.stream(type).anyMatch(i -> i == t.type))
                .sorted(Comparator.comparing(Enum::ordinal))
                .toArray(EnumThemeComponentType[]::new);
    }

}
