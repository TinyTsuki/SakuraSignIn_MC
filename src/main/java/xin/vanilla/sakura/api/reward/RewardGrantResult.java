package xin.vanilla.sakura.api.reward;

import lombok.Value;

@Value
public class RewardGrantResult {
    RewardGrantStatus status;
    String detail;

    public static RewardGrantResult success() {
        return new RewardGrantResult(RewardGrantStatus.SUCCESS, "");
    }

    public static RewardGrantResult of(RewardGrantStatus status, String detail) {
        return new RewardGrantResult(status, detail == null ? "" : detail);
    }

    public boolean isSuccess() {
        return status == RewardGrantStatus.SUCCESS;
    }
}
