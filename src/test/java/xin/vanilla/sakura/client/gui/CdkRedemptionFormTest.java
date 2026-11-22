package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import static org.junit.Assert.*;

public class CdkRedemptionFormTest {
    @Test
    public void respectsConfiguredCommandsAndPreservesCodeCaseAndInternalSpaces() {
        assertEquals("/custom redeem AbC 123", CdkRedemptionForm.command("custom", "redeem", " AbC 123 "));
        assertEquals("/sakura cdk \u6625\u65e5", CdkRedemptionForm.command("sakura", "cdk", "\u6625\u65e5"));
    }

    @Test
    public void rejectsBlankCodesAndChatControlCharacters() {
        for (String code : new String[]{null, "", "  ", "A\nB", "A\rB", "A\tB", "A\u007fB", "A\u00a7B"})
            assertThrows(IllegalArgumentException.class, () -> CdkRedemptionForm.command("sakura", "cdk", code));
    }

    @Test
    public void neverSendsTruncatedCodes() {
        String code = new String(new char[244]).replace('\0', 'x');
        assertEquals(256, CdkRedemptionForm.command("sakura", "cdk", code).length());
        assertThrows(IllegalArgumentException.class, () -> CdkRedemptionForm.command("sakura", "cdk", code + "x"));
    }
}
