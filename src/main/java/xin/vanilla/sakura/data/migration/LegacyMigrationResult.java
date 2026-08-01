package xin.vanilla.sakura.data.migration;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 玩家登录时旧数据迁移的结果。
 */
public enum LegacyMigrationResult implements IEnumDescribable {
    NO_LEGACY_DATA,
    MIGRATED,
    CLEANED_UP;

    @Override
    public Component enumDescription() {
        return SakuraComponent.get().literal(name());
    }
}
