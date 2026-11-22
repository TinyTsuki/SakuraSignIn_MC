package xin.vanilla.sakura.reward.script;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ScriptRewardLanguageSyncTest {
    @Test
    public void lateClientSettingsReplaceInitialEnglishOnlyOnce() {
        ScriptRewardLanguageSync sync = new ScriptRewardLanguageSync();
        Object connection = new Object();
        List<String> sent = new ArrayList<>();
        sync.sync(connection, "en_us", sent::add);
        sync.sync(connection, "zh_cn", sent::add);
        for (int i = 0; i < 100; i++) sync.sync(connection, "ZH-CN", sent::add);
        sync.sync(connection, "en_us", sent::add);
        assertEquals(Arrays.asList("en_us", "zh_cn", "en_us"), sent);
    }

    @Test
    public void reconnectionAndServerRestartCannotReuseOldMetadata() {
        ScriptRewardLanguageSync sync = new ScriptRewardLanguageSync();
        List<String> sent = new ArrayList<>();
        Object first = new String("same player"), second = new String("same player");
        sync.sync(first, "zh_cn", sent::add);
        sync.sync(second, "zh_cn", sent::add);
        sync.remove(first);
        sync.sync(first, "zh_cn", sent::add);
        sync.clear();
        sync.sync(second, "zh_cn", sent::add);
        assertEquals(4, sent.size());
    }

    @Test
    public void failedSendCanRetryAndAbsentConnectionDoesNothing() {
        ScriptRewardLanguageSync sync = new ScriptRewardLanguageSync();
        Object connection = new Object();
        List<String> sent = new ArrayList<>();
        sync.sync(null, "zh_cn", sent::add);
        assertThrows(IllegalStateException.class, () -> sync.sync(connection, "zh_cn", language -> {
            throw new IllegalStateException("send failed");
        }));
        sync.sync(connection, "zh_cn", sent::add);
        sync.sync(new Object(), null, sent::add);
        assertEquals(Arrays.asList("zh_cn", "en_us"), sent);
    }
}
