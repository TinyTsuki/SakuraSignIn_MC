package xin.vanilla.sakura.network.data;

import lombok.Data;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.enums.EnumSignInStatus;

@Data
public class RewardCellSyncData {
    private final EnumSignInStatus status;
    private final String date;
    private final Reward reward;
}
