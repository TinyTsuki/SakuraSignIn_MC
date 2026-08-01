package xin.vanilla.sakura.api.reward;

import lombok.Value;

/**
 * 可定位到奖励内容字段的校验问题。
 */
@Value
public class RewardViolation {
    String field;
    String code;
}
