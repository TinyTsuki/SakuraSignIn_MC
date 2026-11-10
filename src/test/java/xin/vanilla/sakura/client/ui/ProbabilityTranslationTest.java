package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.StringUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.test.UnboundConfigPlatform;

public class ProbabilityTranslationTest {
    @Rule
    public UnboundConfigPlatform platform = new UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void signInProbabilityHasOnePercentInChinese() {
        assertProbability("zh_cn", "aui_probability_s", "概率：");
    }

    @Test
    public void signInProbabilityHasOnePercentInEnglish() {
        assertProbability("en_us", "aui_probability_s", "Probability: ");
    }

    @Test
    public void lotteryProbabilityHasOnePercentInChinese() {
        assertProbability("zh_cn", "lottery_probability_s", "相对概率：");
    }

    @Test
    public void lotteryProbabilityHasOnePercentInEnglish() {
        assertProbability("en_us", "lottery_probability_s", "Relative probability: ");
    }

    @Test
    public void formatterPreservesLiteralPercentAfterTheArgument() {
        assertEquals("12.5%", StringUtils.format("%s%", "12.5"));
        assertEquals("100%", StringUtils.format("%s", "100%"));
    }

    private void assertProbability(String language, String key, String prefix) {
        for (String percent : new String[]{"0", "0.01", "12.5", "100"}) {
            Component component = SakuraComponent.get().transLang(language, "format", key, percent);
            String expected = prefix + percent + "%";
            assertEquals(expected, component.getString(language, true, true));
            assertEquals(expected, component.toVanilla(language).getString());
        }
    }
}
