package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RewardEditorGroupRefreshTest {
    private String groupEditor() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/screen/RewardOptionScreen.java")), StandardCharsets.UTF_8);
        return source.substring(source.indexOf("private void editRewardGroup("),
                source.indexOf("private Screen getPersonalDatePresetInputScreen("));
    }

    @Test
    public void cdkRenameRefreshesRowsBeforeReturningToParent() throws Exception {
        String body = groupEditor();
        body = body.substring(body.indexOf("if (rule == ERewardRule.CDK_REWARD)"), body.indexOf("ruleInputRegex(rule)"));
        assertTrue("CDK rename must rebuild rows before parent AUI binding", body.matches("(?s).*updateKeyName\\(.*?saveEditor\\(\\);\\s*updateLayout\\(\\);.*"));
    }

    @Test
    public void ordinaryRenameRefreshesRowsBeforeReturningToParent() throws Exception {
        String body = groupEditor();
        body = body.substring(body.indexOf("ruleInputRegex(rule)"));
        assertTrue("Rule rename must rebuild rows before parent AUI binding", body.matches("(?s).*updateKeyName\\(.*?saveEditor\\(\\);\\s*updateLayout\\(\\);.*"));
    }
}
