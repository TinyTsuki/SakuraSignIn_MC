package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;
import xin.vanilla.sakura.client.theme.ThemeTexture;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.enums.ESignInStatus;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.screen.SignInCell;

import static org.junit.Assert.assertNull;

public class SignInUiCellAtlasTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void selectedThemeUsesItsStandalonePressedImage() {
        lombok.var old = SakuraClientState.getTheme();
        try {
            for (String id : BuiltInThemeCatalog.themeIds()) {
                lombok.var theme = BuiltInThemeCatalog.load(id);
                SakuraClientState.setTheme(theme);
                for (int i = 0; i < 5; i++)
                    for (boolean selected : new boolean[]{false, true}) {
                        assertTexture(SakuraUiPages.themeIcon(null, theme.getLayout(), i, selected),
                                theme.texture("selector." + BuiltInThemeCatalog.themeIds().get(i) + (selected ? ".pressed" : ".normal")));
                    }
            }
        } finally {
            SakuraClientState.setTheme(old);
        }
    }

    @Test
    public void fiveThemesAndSpecialVariantsUseIndependentImages() {
        lombok.var old = SakuraClientState.getTheme();
        boolean special = SakuraClientState.isSpecialThemeVariant();
        boolean auto = ClientConfig.get().display().autoRewarded();
        try {
            ClientConfig.get().display().autoRewarded(false);
            for (String id : BuiltInThemeCatalog.themeIds()) {
                lombok.var theme = BuiltInThemeCatalog.load(id);
                SakuraClientState.setTheme(theme);
                lombok.var cell = new SignInCell(null, theme.texture("background.calendar").location(), theme.getLayout(), 0, 0, 20, 20, 1,
                        new RewardList(), 2026, 10, 9, ESignInStatus.NOT_SIGNED_IN.getCode()).setShowIcon(true);
                for (boolean variant : new boolean[]{false, true}) {
                    if (variant && !theme.getLayout().isSpecial()) continue;
                    SakuraClientState.setSpecialThemeVariant(variant);
                    for (boolean autoRewarded : new boolean[]{false, true}) {
                        ClientConfig.get().display().autoRewarded(autoRewarded);
                        for (ESignInStatus status : new ESignInStatus[]{ESignInStatus.NO_ACTION, ESignInStatus.CAN_REPAIR, ESignInStatus.NOT_SIGNED_IN}) {
                            cell.setStatus(status.getCode());
                            assertNull("Unsigned dates must not acquire a status icon", cell.uiStatusIcon());
                            org.junit.Assert.assertTrue("Hiding the mark must preserve the date click target", cell.isShowIcon());
                        }
                    }
                    cell.setStatus(ESignInStatus.SIGNED_IN.getCode());
                    assertTexture(cell.uiStatusIcon(), theme.texture("status.signed_in." + (variant ? "special" : "normal")));
                    cell.setStatus(ESignInStatus.REWARDED.getCode());
                    assertTexture(cell.uiStatusIcon(), theme.texture("status.rewarded"));
                }
                ClientConfig.get().display().autoRewarded(true);
                cell.setStatus(ESignInStatus.SIGNED_IN.getCode());
                assertTexture(cell.uiStatusIcon(), theme.texture("status.signed_in." + (SakuraClientState.isSpecialThemeVariant() ? "special" : "normal")));
                ClientConfig.get().display().autoRewarded(false);
                cell.setShowIcon(false);
                assertNull(cell.uiStatusIcon());
            }
        } finally {
            ClientConfig.get().display().autoRewarded(auto);
            SakuraClientState.setTheme(old);
            SakuraClientState.setSpecialThemeVariant(special);
        }
    }

    private static void assertTexture(Texture actual, ThemeTexture expected) {
        assertEquals(expected.location(), actual.location());
        assertEquals(0, actual.u0());
        assertEquals(0, actual.v0());
        assertEquals(expected.width(), actual.uvWidth());
        assertEquals(expected.height(), actual.uvHeight());
        assertEquals(expected.width(), actual.uWidth());
        assertEquals(expected.height(), actual.vHeight());
    }
}
