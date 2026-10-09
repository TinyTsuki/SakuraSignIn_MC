package xin.vanilla.sakura.config;

import net.minecraft.util.registry.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import static org.junit.Assert.*;

public class OpeningTipsPreferenceTest {
    @org.junit.Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void registeredChineseDescriptionSaysEveryOpening() throws Exception {
        assertEquals("每次打开签到界面时显示操作提示", entry(new ConfigBaselineFixture(ClientConfig.class))
                .getTooltipLocalizedByLang().get("zh_cn"));
    }
    @Test public void registeredEnglishDescriptionSaysEveryOpening() throws Exception {
        assertEquals("Show instructions each time the sign-in screen is opened", entry(new ConfigBaselineFixture(ClientConfig.class))
                .getTooltipLocalizedByLang().get("en_us"));
    }
    @Test public void openingPreferenceIsEnabledByDefault() throws Exception {
        assertEquals(Boolean.TRUE, entry(new ConfigBaselineFixture(ClientConfig.class)).getDefaultValue());
    }
    @Test public void explicitNoReminderCanBeSavedWithoutChangingTheDefault() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
        assertTrue(ClientConfig.get().display().showSignInScreenTips());
        ClientConfig.get().display().showSignInScreenTips(false);
        ClientConfig.save();
        assertEquals(Boolean.FALSE, fixture.values.get("display.showSignInScreenTips"));
        assertEquals(1, fixture.saves);
        assertEquals(Boolean.TRUE, entry(fixture).getDefaultValue());
    }
    private static ConfigEntryDescriptor entry(ConfigBaselineFixture fixture) {
        return fixture.descriptors.stream().filter(entry -> entry.getPath().equals("display.showSignInScreenTips"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing opening preference"));
    }
}
