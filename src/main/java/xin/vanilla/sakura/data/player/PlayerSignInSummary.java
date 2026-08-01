package xin.vanilla.sakura.data.player;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;

import java.util.Map;
import java.util.TreeMap;

/**
 * 永久摘要不依赖可清理的月度详情，签到判定与累计统计始终可用。
 */
@Data
@NoArgsConstructor
public class PlayerSignInSummary {
    public static final int SCHEMA_VERSION = 1;

    private int totalSignInDays;
    private int continuousSignInDays;
    private String lastSignInTime = "";
    private int signInCard;
    private boolean autoRewarded;
    private String language = "client";
    private ListNBT cdkRecords = new ListNBT();
    private Map<String, MonthSignInIndex> monthIndexes = new TreeMap<>();
    private boolean legacyCapabilityMigrated;
    private String legacyCapabilityBackup = "";

    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        tag.putInt("totalSignInDays", totalSignInDays);
        tag.putInt("continuousSignInDays", continuousSignInDays);
        tag.putString("lastSignInTime", lastSignInTime);
        tag.putInt("signInCard", signInCard);
        tag.putBoolean("autoRewarded", autoRewarded);
        tag.putString("language", language);
        tag.put("cdkRecords", copyList(cdkRecords));

        ListNBT indexes = new ListNBT();
        monthIndexes.values().forEach(index -> indexes.add(index.serializeNBT()));
        tag.put("monthIndexes", indexes);

        CompoundNBT migration = new CompoundNBT();
        migration.putBoolean("legacyCapabilityMigrated", legacyCapabilityMigrated);
        migration.putString("legacyCapabilityBackup", legacyCapabilityBackup);
        tag.put("migration", migration);
        return tag;
    }

    public static PlayerSignInSummary deserializeNBT(CompoundNBT tag) {
        PlayerSignInSummary summary = new PlayerSignInSummary();
        summary.totalSignInDays = tag.getInt("totalSignInDays");
        summary.continuousSignInDays = tag.getInt("continuousSignInDays");
        summary.lastSignInTime = tag.getString("lastSignInTime");
        summary.signInCard = tag.getInt("signInCard");
        summary.autoRewarded = tag.getBoolean("autoRewarded");
        summary.language = tag.contains("language", 8) ? tag.getString("language") : "client";
        summary.cdkRecords = copyList(tag.getList("cdkRecords", 10));

        ListNBT indexes = tag.getList("monthIndexes", 10);
        for (int i = 0; i < indexes.size(); i++) {
            MonthSignInIndex index = MonthSignInIndex.deserializeNBT(indexes.getCompound(i));
            summary.monthIndexes.put(index.getMonth(), index);
        }

        CompoundNBT migration = tag.getCompound("migration");
        summary.legacyCapabilityMigrated = migration.getBoolean("legacyCapabilityMigrated");
        summary.legacyCapabilityBackup = migration.getString("legacyCapabilityBackup");
        return summary;
    }

    public static boolean isCurrentSchema(CompoundNBT tag) {
        return tag.getInt("schemaVersion") == SCHEMA_VERSION;
    }

    private static ListNBT copyList(ListNBT source) {
        ListNBT copy = new ListNBT();
        for (INBT element : source) {
            copy.add(element.copy());
        }
        return copy;
    }
}
