package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class RewardEditorLayoutTest {
    private static Element document() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                UiBoundaryChecks.ownResources("sakura_sign_in").get("rewards.html"))).getDocumentElement();
    }

    private static Element id(Element root, String id) {
        var nodes = root.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++)
            if (nodes.item(i) instanceof Element element && id.equals(element.getAttribute("id"))) return element;
        throw new AssertionError("Missing control " + id);
    }

    private static Map<String, String> rule(String selector) throws Exception {
        String css = new String(UiBoundaryChecks.ownResources("sakura_sign_in").get("rewards.css"), StandardCharsets.UTF_8);
        var matcher = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]+)}").matcher(css);
        assertTrue("Missing scoped rule " + selector, matcher.find());
        Map<String, String> declarations = new LinkedHashMap<>();
        for (String declaration : matcher.group(1).split(";")) {
            int colon = declaration.indexOf(':');
            if (colon >= 0)
                declarations.put(declaration.substring(0, colon).trim(), declaration.substring(colon + 1).trim());
        }
        return declarations;
    }

    @Test
    public void sharedHeaderKeepsTitleAndIconActionsIndependent() throws Exception {
        Element root = document(), title = id(root, "title"), help = id(root, "help");
        assertNotSame("Wide help must not compete with the title", title.getParentNode(), help.getParentNode());
        assertSame(title.getParentNode().getParentNode(), help.getParentNode().getParentNode());
        assertSame(help.getParentNode(), id(root, "download").getParentNode());
        assertSame(help.getParentNode(), id(root, "close").getParentNode());
        assertEquals("more_panel", ((Element) id(root, "sidebar").getParentNode()).getAttribute("id"));
    }

    @Test
    public void longToolLabelsStayWithinTheirFixedHeight() throws Exception {
        var buttons = rule(".rewards-page button");
        assertEquals("nowrap", buttons.get("white-space"));
        assertEquals("hidden", buttons.get("overflow"));
        assertEquals("ellipsis", buttons.get("text-overflow"));
        assertEquals("0 0 13px", rule(".glyph").get("flex"));
        assertEquals("flex", rule(".rewards-page .header").get("display"));
        assertEquals("hidden", rule(".workspace").get("overflow"));
    }

    @Test
    public void everyExistingActionRetainsOneControlId() throws Exception {
        Element root = document();
        Set<String> expected = new HashSet<>(Set.of("close"));
        for (var action : RewardEditorUiSession.Action.values()) expected.add(action.id);
        Map<String, Integer> counts = new HashMap<>();
        var buttons = root.getElementsByTagName("button");
        for (int i = 0; i < buttons.getLength(); i++) {
            String id = ((Element) buttons.item(i)).getAttribute("id");
            counts.merge(id, 1, Integer::sum);
        }
        for (String id : expected) assertEquals(id, Integer.valueOf(1), counts.get(id));
    }
}
