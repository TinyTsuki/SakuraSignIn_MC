package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class RewardRowHitLayoutTest {
    private String rule(String selector) throws Exception {
        String css = new String(UiBoundaryChecks.ownResources("sakura_sign_in").get("rewards.css"), StandardCharsets.UTF_8);
        lombok.var match = Pattern.compile(Pattern.quote(selector) + "\\{([^}]+)}").matcher(css);
        assertTrue(match.find());
        return match.group(1);
    }

    @Test
    public void selectionIncludesIconDescriptionAndWhitespaceButLeavesToolsSeparate() throws Exception {
        String action = rule(".reward-entry>.row-action");
        assertTrue(action.contains("left:20px"));
        assertTrue(action.contains("right:78px"));
        assertTrue(action.contains("height:28px"));
        assertTrue(action.contains("max-height:28px"));
    }

    @Test
    public void descriptionPassesPointerInputToTheExistingSelectionButton() throws Exception {
        assertTrue(rule(".reward-entry>.label").contains("pointer-events:none"));
        assertTrue(rule(".reward-entry .editor>.input").contains("left:2px"));
        assertTrue(rule(".reward-entry .editor>.tool:nth-child(4)").contains("right:0"));
    }
}
