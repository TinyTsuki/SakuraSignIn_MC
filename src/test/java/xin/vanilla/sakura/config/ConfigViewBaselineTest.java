package xin.vanilla.sakura.config;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ConfigViewBaselineTest {
    @org.junit.Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @Test
    public void clientListsAreSnapshotsAndEmptyThemeIsNotReplaced() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
        ClientConfigView view = ClientConfigView.get();
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
        fixture.bind(CommonConfig.class);
        CommonConfigView view = CommonConfigView.get();
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
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        baseline.put("unbound", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        fixture.bind(CommonConfig.class);
        baseline.put("defaults", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        ConfigBaselineFixture.assertSnapshot("common", baseline);
    }

    @Test
    public void clientPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        ConfigBaselineFixture.bind(ClientConfig.class, null);
        baseline.put("unbound", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        fixture.bind(ClientConfig.class);
        baseline.put("defaults", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        ConfigBaselineFixture.assertSnapshot("client", baseline);
    }
}
