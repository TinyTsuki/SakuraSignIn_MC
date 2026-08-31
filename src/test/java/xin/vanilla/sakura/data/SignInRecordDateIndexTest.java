package xin.vanilla.sakura.data;

import org.junit.Test;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SignInRecordDateIndexTest {
    @Test
    public void rebuildsWhenTheLoadedRecordListGrows() {
        List<SignInRecord> records = new ArrayList<>();
        SignInRecordDateIndex index = new SignInRecordDateIndex();
        int date = 20260831;

        records.add(record("2026-08-31 00:00:00"));
        assertEquals(1, index.recordsOn(records, date).size());

        records.add(record("2026-08-31 12:00:00"));
        assertEquals(2, index.recordsOn(records, date).size());
    }

    @Test
    public void indexesOnlyTheRequestedDate() {
        List<SignInRecord> records = new ArrayList<>();
        records.add(record("2026-08-30 00:00:00"));
        records.add(record("2026-08-31 00:00:00"));

        assertEquals(1, new SignInRecordDateIndex().recordsOn(records, 20260831).size());
    }

    private static SignInRecord record(String date) {
        SignInRecord record = new SignInRecord();
        record.setCompensateTime(DateUtils.format(date));
        return record;
    }
}
