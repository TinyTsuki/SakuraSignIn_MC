package xin.vanilla.sakura.client.ui;

import org.junit.*;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.sakura.client.theme.*;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.enums.ESignInStatus;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.screen.SignInCell;
import xin.vanilla.sakura.screen.coordinate.*;
import static org.junit.Assert.*;

public class SignInUiCellAtlasTest {
    @Test public void selectedThemeUsesTheOriginalPressedAtlasRegion() {
        for (String id : BuiltInThemeCatalog.themeIds()) {
            BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load(id);
            TextureCoordinate coordinates = theme.getCoordinates();
            coordinates.getThemeTapUV().setV0(41);
            for (int index = 0; index < 5; index++) {
                Texture selected = SakuraUiPages.themeIcon(theme.textureLocation(), coordinates, index, true);
                assertEquals("Selected theme needs its pressed atlas region", 41, selected.v0());
                assertEquals((int) (coordinates.getThemeTapUV().getU0() + index * coordinates.getThemeUV().getUWidth()), selected.u0());
                Texture normal = SakuraUiPages.themeIcon(theme.textureLocation(), coordinates, index, false);
                assertEquals((int) coordinates.getThemeUV().getV0(), normal.v0());
            }
        }
    }
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() { SignInUiParityTest.bootstrap(); }
    @Test public void fiveThemesAndSpecialOffsetsKeepTheOriginalAtlasIdentity() {
        boolean auto = ClientConfig.get().display().autoRewarded();
        try {
            ClientConfig.get().display().autoRewarded(false);
            for (String id : BuiltInThemeCatalog.themeIds()) {
                BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load(id);
                TextureCoordinate coordinates = theme.getCoordinates();
                SignInCell cell = new SignInCell(null, theme.textureLocation(), coordinates, 0, 0, 20, 20, 1,
                        new RewardList(), 2026, 10, 9, ESignInStatus.NOT_SIGNED_IN.getCode()).setShowIcon(true);
                assertTexture(cell.uiStatusIcon(), theme, coordinates.getNotSignedInUV(), true);
                coordinates.getNotSignedInUV().setX(320);
                assertTexture(cell.uiStatusIcon(), theme, coordinates.getNotSignedInUV(), true);
                cell.setStatus(ESignInStatus.SIGNED_IN.getCode()); coordinates.getSignedInUV().setX(320);
                assertTexture(cell.uiStatusIcon(), theme, coordinates.getSignedInUV(), true);
                cell.setStatus(ESignInStatus.REWARDED.getCode());
                assertTexture(cell.uiStatusIcon(), theme, coordinates.getRewardedUV(), false);
                cell.setShowIcon(false); assertNull(cell.uiStatusIcon());
            }
        } finally { ClientConfig.get().display().autoRewarded(auto); }
    }
    @Test public void replacementPackCoordinatesAreNotReplacedByHardcodedUvs() {
        BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load("sakura");
        TextureCoordinate coordinates = theme.getCoordinates();
        coordinates.getRewardedUV().setU0(27).setV0(41).setUWidth(13).setVHeight(17);
        SignInCell cell = new SignInCell(null, theme.textureLocation(), coordinates, 0, 0, 20, 20, 1,
                new RewardList(), 2026, 10, 9, ESignInStatus.REWARDED.getCode()).setShowIcon(true);
        Texture texture = cell.uiStatusIcon();
        assertEquals(27, texture.u0()); assertEquals(41, texture.v0()); assertEquals(13, texture.uWidth()); assertEquals(17, texture.vHeight());
    }
    private static void assertTexture(Texture actual, BuiltInThemeDescriptor theme, Coordinate uv, boolean offset) {
        assertEquals(theme.textureLocation(), actual.location());
        assertEquals(theme.getCoordinates().getTotalWidth(), actual.uvWidth());
        assertEquals(theme.getCoordinates().getTotalHeight(), actual.uvHeight());
        assertEquals((int) (uv.getU0() + (offset ? uv.getX() : 0)), actual.u0());
        assertEquals((int) (uv.getV0() + (offset ? uv.getY() : 0)), actual.v0());
    }
}
