package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SakuraUiRouteTest {
    @Test
    public void existingEntryFactoriesReachSharedOwnersAndForms() throws Exception {
        String quick = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/gui/SakuraQuickActions.java"), StandardCharsets.UTF_8);
        String events = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/event/ClientEventHandler.java"), StandardCharsets.UTF_8);
        String presets = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/gui/PersonalDatePresetForm.java"), StandardCharsets.UTF_8);
        assertTrue(quick.contains("new PersonalDateConfigScreen"));
        assertTrue(quick.contains("new ConfigEditorScreen"));
        assertTrue(quick.contains("ClientEventHandler.openSignInScreen"));
        assertTrue(events.contains("new SignInScreen()"));
        assertTrue(presets.contains("new InputFormScreen(args)"));
    }

    @Test
    public void dateRowsUseTheUnconditionalSharedCommandButton() throws Exception {
        String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/ui/PersonalDateUiSession.java"), StandardCharsets.UTF_8);
        assertTrue("Date rows need a real command listener without a picker input", source.contains("row.onAction(\"apply\""));
        assertFalse(source.contains("row.onAction(\"pick\""));
    }

    @Test
    public void signInAndPersonalDatesUsePublicSharedPageBoundary() throws Exception {
        for (String name : new String[]{"SignInScreen", "PersonalDateConfigScreen"}) {
            String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/" + name + ".java"), StandardCharsets.UTF_8);
            assertTrue(name + " needs the public shared-page contract", source.contains("UiPageSource"));
            assertTrue(source.contains("pageSession()"));
            assertTrue(source.contains("BaniraUi.screenRemoved"));
            assertFalse(source.contains("xin.vanilla.banira.internal."));
        }
    }

    @Test
    public void packetRefreshCanReachTheActiveSharedSignInSession() throws Exception {
        String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/network/ClientProxy.java"), StandardCharsets.UTF_8);
        assertTrue("AUI wrappers need an explicit presentation revision", source.contains("SakuraUiPages.signInChanged()"));
    }

    @Test
    public void allOwnedPagesHaveLightweightLocalTemplates() throws Exception {
        for (String name : new String[]{"signin", "personal-date"}) {
            Path path = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/" + name + ".html");
            assertTrue("Missing " + path, Files.exists(path));
            String html = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(html.contains("banira_codex/shared.css"));
            assertFalse(html.contains("<script"));
            assertFalse(html.contains("https:"));
            assertTrue(Files.size(path) <= 12288);
        }
    }

    @Test
    public void sharedViewsDoNotIssueRewardOrDatePacketsAndHaveAllCalendarNodes() throws Exception {
        for (String name : new String[]{"SignInUiSession", "PersonalDateUiSession"}) {
            String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/ui/" + name + ".java"), StandardCharsets.UTF_8);
            assertFalse(source.contains("sendToServer"));
            assertFalse(source.contains("RewardManager"));
            assertFalse(source.contains("xin.vanilla.banira.internal."));
        }
        String html = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/signin.html"), StandardCharsets.UTF_8);
        for (int index = 0; index < 40; index++) {
            assertTrue(html.contains("id=\"day" + index + "\""));
            assertTrue(html.contains("id=\"status" + index + "\""));
        }
        for (int index = 0; index < 7; index++) assertTrue(html.contains("id=\"weekday" + index + "\""));
    }
}
