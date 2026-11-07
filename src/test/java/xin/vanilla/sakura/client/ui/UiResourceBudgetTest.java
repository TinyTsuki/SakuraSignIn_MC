package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class UiResourceBudgetTest {
    @Test
    public void approvedStyleBundleKeepsItsFiniteCeiling() {
        assertEquals(40960, UiBoundaryChecks.stylesBudget(1));
        var files = new java.util.TreeMap<String, byte[]>();
        for (int i = 0; i < 10; i++) files.put("piece" + i + ".css", new byte[4096]);
        assertTrue(UiBoundaryChecks.resources(files, false).isEmpty());
        files.put("overflow.css", new byte[1]);
        assertTrue(UiBoundaryChecks.resources(files, false).contains("CSS budget"));
    }

    @Test
    public void everyOwnedResourceUsesLocalSharedStylesAndStaysWithinBudget() throws Exception {
        Map<String, byte[]> files = UiBoundaryChecks.ownResources("sakura_sign_in");
        assertFalse(files.isEmpty());
        assertEquals(List.of(), UiBoundaryChecks.resources(files, false));
        for (var entry : files.entrySet())
            if (entry.getKey().endsWith(".html"))
                assertTrue(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8).contains("../banira_codex/shared.css"));
    }

    @Test
    public void oversizedScriptsFontsBitmapsAndRemoteResourcesAreRejected() {
        for (var fixture : List.of(Map.of("page.html", "<script src='https://example.invalid/a.js'></script>".getBytes(StandardCharsets.UTF_8)),
                Map.of("large.html", new byte[12289]), Map.of("mod.css", new byte[4097]), Map.of("font.woff", new byte[1]),
                Map.of("large.png", new byte[65537]), Map.of("large.js", new byte[8193])))
            assertFalse(UiBoundaryChecks.resources(fixture, false).isEmpty());
    }

    @Test
    public void copiedSharedStylesAndUnsafeReferencesAreRejected() {
        assertFalse(UiBoundaryChecks.resources(Map.of("shared.css", new byte[1]), false).isEmpty());
        assertFalse(UiBoundaryChecks.resources(Map.of("page.html", "<link href='../foreign/file.css'>".getBytes(StandardCharsets.UTF_8)), false).isEmpty());
    }

    @Test
    public void independentPagesKeepSeparateFourKiBStyleBudgets() {
        var a = new byte[3000];
        var b = new byte[3000];
        assertTrue(UiBoundaryChecks.resources(Map.of("a.css", a, "b.css", b,
                "a.html", "<link href=\"a.css\">".getBytes(StandardCharsets.UTF_8),
                "b.html", "<link href=\"b.css\">".getBytes(StandardCharsets.UTF_8)), false).isEmpty());
        assertFalse(UiBoundaryChecks.resources(Map.of("a.css", a, "b.css", b,
                "a.html", "<link href=\"a.css\"><link href=\"b.css\">".getBytes(StandardCharsets.UTF_8)), false).isEmpty());
    }

    @Test
    public void everySakuraPageUsesItsOwnPrimaryColorFallback() throws Exception {
        var files = UiBoundaryChecks.ownResources("sakura_sign_in");
        int pages = 0;
        for (var entry : files.entrySet())
            if (entry.getKey().endsWith(".html")) {
                pages++;
                assertTrue(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8).contains("<body class=\"sakura-ui"));
            }
        assertEquals(5, pages);
    }
}
