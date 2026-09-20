package xin.vanilla.sakura.config;

import org.junit.Test;
import xin.vanilla.sakura.config.access.ClientConfigAccess;
import xin.vanilla.sakura.config.access.CommonConfigAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ConfigViewBaselineTest {
    @Test
    public void clientListsAreSnapshotsAndEmptyThemeIsNotReplaced() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        ClientConfig.RootView view = ClientConfigAccess.root(fixture.holder);
        fixture.values.put("signKeys.lastMonth", Arrays.asList("A,B", "CTRL+C"));
        assertEquals(Arrays.asList("A,B", "CTRL+C"), view.signKeys().lastMonth());
        view.signKeys().lastMonth().clear();
        assertEquals(2, view.signKeys().lastMonth().size());
        fixture.values.put("display.themeId", "");
        assertEquals("", view.display().themeId());
        fixture.values.put("display.themeId", null);
        assertEquals("sakura", view.display().themeId());
        assertEquals(0, fixture.saves);
    }

    @Test
    public void commonDecimalAndNullDefaultsDoNotTriggerSaves() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        CommonConfig.RootView view = CommonConfigAccess.root(fixture.holder);
        view.cooling().timeCoolingInterval(0.125D);
        assertEquals(0.125D, view.cooling().timeCoolingInterval(), 0.0D);
        fixture.values.put("command.commandPrefix", "");
        assertEquals("", view.command().commandPrefix());
        fixture.values.put("command.commandPrefix", null);
        assertEquals("sakura", view.command().commandPrefix());
        assertEquals(0, fixture.saves);
        fixture.holder.save();
        assertEquals(1, fixture.saves);
    }

    @Test
    public void commonPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        baseline.put("unbound", ConfigBaselineFixture.readView(CommonConfigAccess.root(null), CommonConfig.RootView.class));
        baseline.put("defaults", ConfigBaselineFixture.readView(CommonConfigAccess.root(fixture.holder), CommonConfig.RootView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(CommonConfigAccess.root(fixture.holder), CommonConfig.RootView.class));
        ConfigBaselineFixture.assertSnapshot("common", baseline);
    }

    @Test
    public void clientPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        baseline.put("unbound", ConfigBaselineFixture.readView(ClientConfigAccess.root(null), ClientConfig.RootView.class));
        baseline.put("defaults", ConfigBaselineFixture.readView(ClientConfigAccess.root(fixture.holder), ClientConfig.RootView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(ClientConfigAccess.root(fixture.holder), ClientConfig.RootView.class));
        ConfigBaselineFixture.assertSnapshot("client", baseline);
    }
}
