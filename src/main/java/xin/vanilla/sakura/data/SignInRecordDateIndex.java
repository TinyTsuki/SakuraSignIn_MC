package xin.vanilla.sakura.data;

import xin.vanilla.banira.common.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only date lookup cache for the currently loaded sign-in detail records. */
public final class SignInRecordDateIndex {
    private List<SignInRecord> source;
    private int sourceSize = -1;
    private Map<Integer, List<SignInRecord>> recordsByDate = Collections.emptyMap();

    public List<SignInRecord> recordsOn(List<SignInRecord> records, int dateKey) {
        if (source != records || sourceSize != records.size()) {
            rebuild(records);
        }
        return recordsByDate.getOrDefault(dateKey, Collections.emptyList());
    }

    private void rebuild(List<SignInRecord> records) {
        Map<Integer, List<SignInRecord>> indexed = new HashMap<>();
        for (SignInRecord record : records) {
            if (record == null || record.getCompensateTime() == null) continue;
            indexed.computeIfAbsent(DateUtils.toDateInt(record.getCompensateTime()), ignored -> new ArrayList<>())
                    .add(record);
        }
        source = records;
        sourceSize = records.size();
        recordsByDate = indexed;
    }
}
