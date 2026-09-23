package xin.vanilla.sakura.config;

import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.data.player.HistoryRetentionPolicy;

import static org.junit.Assert.*;

public class ConfigNetworkSnapshotTest {
    @org.junit.Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test public void snapshotReflectsGeneratedWritesWithoutSaving() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        CommonConfigView view = CommonConfigView.get();
        view.cooling().timeCoolingInterval(0.125D);
        view.history().retentionMonths(6).retentionPolicy(HistoryRetentionPolicy.DELETE_MONTH_FILE);
        String longCommand = String.join("", java.util.Collections.nCopies(400, "command,part"));
        view.command().commandPrefix(longCommand);
        java.util.Map<String, String> snapshot = CommonConfig.networkSnapshot();
        assertEquals(fixture.defaults.keySet(), snapshot.keySet());
        assertEquals("0.125", snapshot.get("cooling.timeCoolingInterval"));
        assertEquals("6", snapshot.get("history.retentionMonths"));
        assertEquals("DELETE_MONTH_FILE", snapshot.get("history.retentionPolicy"));
        assertEquals(longCommand, snapshot.get("command.commandPrefix"));
        assertEquals(0, fixture.saves);
        fixture.values.put("command.commandPrefix", null);
        assertEquals("", CommonConfig.networkSnapshot().get("command.commandPrefix"));
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        assertTrue(CommonConfig.networkSnapshot().isEmpty());
    }
}
