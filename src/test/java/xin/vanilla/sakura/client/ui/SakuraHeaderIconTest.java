package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;

import static org.junit.Assert.assertTrue;

public class SakuraHeaderIconTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void lotteryBrandUsesTheStandaloneModSmallIcon() {
        var port = new LotteryUiOperationHandoffTest.Port();
        var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        try {
            assertEquals(UiGameElement.Kind.SPRITE, view.icons.get("brand").kind());
            var texture = view.icons.get("brand").option().texture()[0];
            assertEquals("sakura_sign_in", texture.location().getNamespace());
            assertTrue(texture.location().getPath().endsWith("/icons/sign_in.png"));
        } finally {
            session.close(xin.vanilla.banira.api.client.ui.UiCloseReason.CANCEL);
        }
    }

    @Test
    public void everyCurrentThemeAndMissingThemeUseFullImages() {
        var old = SakuraClientState.getTheme();
        try {
            for (String id : BuiltInThemeCatalog.themeIds()) {
                var theme = BuiltInThemeCatalog.load(id);
                SakuraClientState.setTheme(theme);
                assertEquals(theme.texture("icon.sign_in").location(), SakuraUiPages.moduleIcon().option().texture()[0].location());
                var texture = SakuraUiPages.moduleIcon().option().texture()[0];
                assertEquals(0, texture.u0());
                assertEquals(0, texture.v0());
                assertEquals(texture.uvWidth(), texture.uWidth());
                assertEquals(texture.uvHeight(), texture.vHeight());
            }
            SakuraClientState.setTheme(null);
            assertEquals(BuiltInThemeCatalog.load("sakura").texture("icon.sign_in").location(), SakuraUiPages.moduleIcon().option().texture()[0].location());
        } finally {
            SakuraClientState.setTheme(old);
        }
    }
}
