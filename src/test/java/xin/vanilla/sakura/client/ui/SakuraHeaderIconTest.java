package xin.vanilla.sakura.client.ui;

import org.junit.*;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class SakuraHeaderIconTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() { SignInUiParityTest.bootstrap(); }
    @Test public void lotteryBrandUsesTheExistingModSmallIconRatherThanAnItem() throws Exception {
        var port = new LotteryUiOperationHandoffTest.Port(); var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true); session.bind(view);
        assertEquals(UiGameElement.Kind.SPRITE, view.icons.get("brand").kind());
        var texture = view.icons.get("brand").option().texture()[0];
        assertEquals("sakura_sign_in", texture.location().getNamespace());
        assertTrue(texture.location().getPath().startsWith("textures/gui/sign_in_calendar_"));
        session.close(xin.vanilla.banira.api.client.ui.UiCloseReason.CANCEL);
    }
    @Test public void unavailableThemeStillHasAValidSmallIconAtlasRegion() throws Exception {
        Method icon;
        try { icon = SakuraUiPages.class.getDeclaredMethod("moduleIcon"); }
        catch (NoSuchMethodException failure) { fail("Missing Sakura mod icon resolver"); return; }
        UiGameElement element = (UiGameElement) icon.invoke(null);
        assertEquals(UiGameElement.Kind.SPRITE, element.kind());
        var texture = element.option().texture()[0];
        assertTrue(texture.uWidth() > 0 && texture.vHeight() > 0);
        assertTrue(texture.u0() >= 0 && texture.v0() >= 0);
        assertTrue(texture.u0() + texture.uWidth() <= texture.uvWidth());
        assertTrue(texture.v0() + texture.vHeight() <= texture.uvHeight());
    }
    @Test public void currentThemeCropIsUsedAndInvalidCropFallsBackAsOnePair() throws Exception {
        var oldAtlas = xin.vanilla.sakura.client.SakuraClientState.getThemeTexture();
        var oldCoordinate = xin.vanilla.sakura.client.SakuraClientState.getThemeTextureCoordinate();
        try {
            var coordinates = new xin.vanilla.sakura.screen.coordinate.TextureCoordinate();
            var atlas = new net.minecraft.resources.ResourceLocation("sakura_sign_in", "textures/gui/sign_in_calendar_sakura.png");
            xin.vanilla.sakura.client.SakuraClientState.setThemeTexture(atlas);
            xin.vanilla.sakura.client.SakuraClientState.setThemeTextureCoordinate(coordinates);
            Method icon;
            try { icon = SakuraUiPages.class.getDeclaredMethod("moduleIcon"); }
            catch (NoSuchMethodException failure) { fail("Missing Sakura mod icon resolver"); return; }
            var current = ((UiGameElement) icon.invoke(null)).option().texture()[0];
            assertEquals(atlas, current.location()); assertEquals(120, current.u0()); assertEquals(920, current.v0());
            coordinates.getSignInBtnUV().setUWidth(Double.NaN);
            var fallback = ((UiGameElement) icon.invoke(null)).option().texture()[0];
            assertEquals("textures/gui/sign_in_calendar_original.png", fallback.location().getPath());
            assertEquals(60, fallback.uWidth()); assertEquals(60, fallback.vHeight());
        } finally {
            xin.vanilla.sakura.client.SakuraClientState.setThemeTexture(oldAtlas);
            xin.vanilla.sakura.client.SakuraClientState.setThemeTextureCoordinate(oldCoordinate);
        }
    }
}
