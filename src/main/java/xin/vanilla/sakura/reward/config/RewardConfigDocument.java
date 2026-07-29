package xin.vanilla.sakura.reward.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 奖励配置的持久化文档，版本升级只在编解码层处理。
 */
@Data
@NoArgsConstructor
public class RewardConfigDocument {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<RewardGroup> groups = new ArrayList<>();

    public RewardConfigDocument(List<RewardGroup> groups) {
        this.groups = groups == null ? new ArrayList<>() : groups;
    }
}
