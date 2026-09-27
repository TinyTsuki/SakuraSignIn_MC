package xin.vanilla.sakura.config;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GeneratedConfigSchemaTest {
    @org.junit.Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @BeforeClass public static void bootstrap() { xin.vanilla.sakura.test.NeoForgeUnitTestBootstrap.bootstrap(); }

    @Test public void commonSchemaMatchesRegistration() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        assertEquals(fixture.defaults, ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
    }

    @Test public void clientSchemaMatchesRegistration() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
        assertEquals(fixture.defaults, ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
    }

    @Test public void shortcutListsAreIndependentAndKeepCommas() throws Exception {
        ConfigBaselineFixture.bind(ClientConfig.class, null);
        List<String> original = new ArrayList<>(ClientConfigView.get().signKeys().lastMonth());
        ClientConfigView.get().signKeys().lastMonth().clear();
        assertEquals(original, ClientConfigView.get().signKeys().lastMonth());
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
        List<String> rules = Arrays.asList("A,B", "CTRL+C");
        ClientConfigView.get().signKeys().lastMonth(rules);
        assertEquals(rules, ClientConfigView.get().signKeys().lastMonth());
        ClientConfigView.get().signKeys().lastMonth().clear();
        assertEquals(rules, fixture.holder.get("signKeys.lastMonth"));
        assertEquals(0, fixture.saves);
        ClientConfigView.get().handle().save();
        assertEquals(1, fixture.saves);
    }
}
