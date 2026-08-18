package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.Test;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定旧 Capability 到摘要、月索引和月度详情的转换语义。
 */
public class LegacyPlayerDataParserTest {

    @Test
    public void preservesSummaryAndBuildsIndependentMonthIndexes() {
        CompoundTag legacy = legacyData();

        LegacyPlayerData parsed = new LegacyPlayerDataParser().parse(legacy);
        PlayerSignInSummary summary = parsed.getSummary();

        assertEquals(37, summary.getTotalSignInDays());
        assertEquals(5, summary.getContinuousSignInDays());
        assertEquals("2024-02-02 09:10:11", summary.getLastSignInTime());
        assertEquals(4, summary.getSignInCard());
        assertTrue(summary.isAutoRewarded());
        assertEquals("zh_cn", summary.getLanguage());
        assertEquals(1, summary.getCdkRecords().size());

        MonthSignInIndex january = summary.getMonthIndexes().get("2024-01");
        MonthSignInIndex february = summary.getMonthIndexes().get("2024-02");
        assertTrue(january.isSigned(31));
        assertFalse(january.isRewarded(31));
        assertTrue(february.isSigned(2));
        assertTrue(february.isRewarded(2));

        List<CompoundTag> januaryRecords = parsed.getRecordsByMonth().get("2024-01");
        assertEquals(1, januaryRecords.size());
        assertEquals("[{\"type\":\"ITEM\"}]", januaryRecords.get(0).getString("rewardList"));
        assertEquals(1, parsed.getRecordsByMonth().get("unknown").size());
    }

    private static CompoundTag legacyData() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("totalSignInDays", 37);
        legacy.putInt("continuousSignInDays", 5);
        legacy.putString("lastSignInTime", "2024-02-02 09:10:11");
        legacy.putInt("signInCard", 4);
        legacy.putBoolean("autoRewarded", true);
        legacy.putString("language", "zh_cn");

        ListTag records = new ListTag();
        records.add(record("2024-01-31 08:00:00", false));
        records.add(record("2024-02-02 09:10:11", true));
        records.add(record("not-a-date", false));
        legacy.put("signInRecords", records);

        CompoundTag cdk = new CompoundTag();
        cdk.putString("key", "WELCOME");
        cdk.putString("date", "2024-02-02 09:11:00");
        cdk.putBoolean("value", true);
        ListTag cdkRecords = new ListTag();
        cdkRecords.add(cdk);
        legacy.put("cdkRecords", cdkRecords);
        return legacy;
    }

    private static CompoundTag record(String compensateTime, boolean rewarded) {
        CompoundTag record = new CompoundTag();
        record.putString("compensateTime", compensateTime);
        record.putString("signInTime", compensateTime);
        record.putString("signInUUID", "8c7b147e-d533-4be2-8242-5ec44310d77d");
        record.putBoolean("rewarded", rewarded);
        record.putString("rewardList", "[{\"type\":\"ITEM\"}]");
        return record;
    }
}
