package xin.vanilla.sakura.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumSignInStatus;

/**
 * 签到界面奖励单元格
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RewardCell {
    private EnumSignInStatus status;
    private String date;
    private RewardList rewards;
}
