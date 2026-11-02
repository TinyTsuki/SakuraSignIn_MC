package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class UiViewportContractTest {
    @Test
    public void everyOwnedPageAndHudDeclaresGuiCoordinates() throws Exception {
        int templates = 0;
        for (var entry : UiBoundaryChecks.ownResources("sakura_sign_in").entrySet()) {
            if (!entry.getKey().endsWith(".html")) continue;
            templates++;
            assertEquals(entry.getKey(), List.of("mode=gui"),
                    modes(new String(entry.getValue(), StandardCharsets.UTF_8)));
        }
        assertTrue("Owned templates required", templates > 0);
    }

    @Test
    public void viewportMetadataUsesHtmlAttributesAndHeadScope() throws Exception {
        assertEquals(List.of("mode=gui"), modes("<HTML><HEAD><META CONTENT='mode=gui' NAME='aui-viewport'></HEAD><BODY></BODY></HTML>"));
    }

    @Test
    public void missingBrowserDuplicateBodyAndCommentDeclarationsDoNotPass() throws Exception {
        for (String html : List.of("<html><head></head><body></body></html>",
                "<html><head><meta name='aui-viewport' content='mode=browser'></head></html>",
                "<html><head><meta name='aui-viewport' content='mode=gui'><meta name='aui-viewport' content='mode=gui'></head></html>",
                "<html><head></head><body><meta name='aui-viewport' content='mode=gui'></body></html>",
                "<html><head><!-- <meta name='aui-viewport' content='mode=gui'> --></head></html>"))
            assertNotEquals(List.of("mode=gui"), modes(html));
    }

    private static List<String> modes(String html) throws Exception {
        List<String> modes = new ArrayList<>();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private boolean head;

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag == HTML.Tag.HEAD) head = true;
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (tag == HTML.Tag.HEAD) head = false;
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (head && tag == HTML.Tag.META && "aui-viewport".equals(attributes.getAttribute(HTML.Attribute.NAME)))
                    modes.add(String.valueOf(attributes.getAttribute(HTML.Attribute.CONTENT)));
            }
        }, true);
        return modes;
    }
}
