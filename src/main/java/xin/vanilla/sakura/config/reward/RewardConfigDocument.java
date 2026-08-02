package xin.vanilla.sakura.config.reward;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.lottery.LotteryPool;

/**
 * 奖励配置的持久化文档，版本升级只在编解码层处理。
 */
@Data
@NoArgsConstructor
public class RewardConfigDocument {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<RewardGroup> groups = new ArrayList<>();
    private List<PersonalDatePreset> personalDatePresets = new ArrayList<>();
    private List<LotteryPool> lotteryPools = new ArrayList<>();

    public RewardConfigDocument(List<RewardGroup> groups) {
        this.groups = groups == null ? new ArrayList<>() : groups;
    }
}
