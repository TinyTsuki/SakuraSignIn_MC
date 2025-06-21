package xin.vanilla.sakura.screen.theme;

import lombok.Data;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumRelationalOperator;
import xin.vanilla.sakura.enums.EnumRenderConditionType;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 渲染条件
 */
@Data
@Accessors(chain = true)
public class RenderCondition implements Serializable, Cloneable {
    /**
     * 条件类型
     */
    private EnumRenderConditionType type;
    /**
     * 关系类型
     */
    private EnumRelationalOperator operator;
    /**
     * 条件集合
     */
    private RenderConditionList children;
    /**
     * 条件值
     */
    private String param;

    @Override
    public RenderCondition clone() {
        try {
            RenderCondition cloned = (RenderCondition) super.clone();
            if (this.children != null) {
                cloned.setChildren(this.children.clone());
            }
            return cloned;
        } catch (Exception e) {
            return new RenderCondition();
        }
    }

    @Data
    @Accessors(chain = true)
    public static class Args {
        private Date time;
        private int continuousDays;
        private int totalDays;
        private long screenDurations;
        private Set<String> params = new HashSet<>();
    }

    public boolean isValid(Args args) {
        AtomicBoolean result = new AtomicBoolean(true);
        if (this.children != null) {
            switch (this.children.getLogicType()) {
                case AND:
                    this.children.forEach(child -> result.set(result.get() && child.isValid(args)));
                    break;
                case OR:
                    this.children.forEach(child -> result.set(result.get() || child.isValid(args)));
                    break;
                case NOT:
                    this.children.forEach(child -> result.set(!child.isValid(args)));
                    break;
            }
        }
        return result.get() && isValid(args, this);
    }

    public static boolean isValid(Args args, RenderCondition condition) {
        if (condition.getOperator() == null
                || condition.getType() == null
                || condition.getParam() == null
                || args == null
        ) return true;
        Object l, r;
        switch (condition.getType()) {
            case DATE: {
                l = DateUtils.toString(DateUtils.format(condition.getParam()), DateUtils.DATE_FORMAT);
                r = DateUtils.toString(args.getTime(), DateUtils.DATE_FORMAT);
            }
            break;
            case TIME: {
                l = DateUtils.toString(DateUtils.format(condition.getParam()), DateUtils.HMS_FORMAT);
                r = DateUtils.toString(args.getTime(), DateUtils.HMS_FORMAT);
            }
            break;
            case YEAR: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getYearPart(args.getTime());
            }
            break;
            case MONTH: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getMonthOfDate(args.getTime());
            }
            break;
            case DAY: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getDayOfMonth(args.getTime());
            }
            break;
            case HOUR: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getHourOfDay(args.getTime());
            }
            break;
            case MINUTE: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getMinute(args.getTime());
            }
            break;
            case SECOND: {
                l = StringUtils.toInt(condition.getParam());
                r = DateUtils.getSecond(args.getTime());
            }
            break;
            case CONTINUOUS_SIGN_IN_DAYS: {
                l = StringUtils.toInt(condition.getParam());
                r = args.getContinuousDays();
            }
            break;
            case TOTAL_SIGN_IN_DAYS: {
                l = StringUtils.toInt(condition.getParam());
                r = args.getTotalDays();
            }
            break;
            case SCREEN_OPENING_DURATION: {
                l = StringUtils.toInt(condition.getParam());
                r = args.getScreenDurations();
            }
            break;
            case CONTAINS_PARAM: {
                boolean result = false;
                for (String param : args.getParams()) {
                    result = result || condition.getOperator().isValid(condition.getParam(), param);
                }
                return result;
            }
            default: {
                return false;
            }
        }
        return condition.getOperator().isValid(l, r);
    }
}
