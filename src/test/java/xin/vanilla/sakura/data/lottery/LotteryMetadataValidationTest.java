package xin.vanilla.sakura.data.lottery;

import com.google.gson.Gson;
import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;

import static org.junit.Assert.*;

public class LotteryMetadataValidationTest {
    private static final Gson GSON = new Gson();

    private LotteryPool pool(String description, String expression) {
        com.google.gson.JsonObject value = GSON.toJsonTree(new LotteryPool("daily", "Daily",
                LotteryLimitPolicy.DAILY, 1, 0, new RewardList())).getAsJsonObject();
        value.addProperty("description", description);
        value.addProperty("voucherExpression", expression);
        return GSON.fromJson(value, LotteryPool.class);
    }

    @Test
    public void acceptsDescriptionBoundaryAndNewlines() {
        assertTrue(LotteryPoolValidator.validate(pool(repeat('a', 2047) + "\n", "")).isEmpty());
    }

    @Test
    public void rejectsDescriptionOverflow() {
        assertTrue(LotteryPoolValidator.validate(pool(repeat('a', 2049), ""))
                .contains("invalid_description"));
    }

    @Test
    public void acceptsExpressionBoundary() {
        String expression = "xpPoints(1)";
        assertTrue(LotteryPoolValidator.validate(pool("", expression
                + repeat(' ', 4096 - expression.length()))).isEmpty());
    }

    @Test
    public void rejectsExpressionOverflow() {
        assertTrue(LotteryPoolValidator.validate(pool("", "xpPoints(1)" + repeat(' ', 4097)))
                .contains("invalid_voucher_expression"));
    }

    @Test
    public void rejectsUnknownAndExecutableExpressions() {
        for (String expression : new String[]{"unknown(1)", "System.exit(0)", "xpPoints(-1)",
                "xpPoints(1);", "item(\"minecraft:paper\",1)&&xpLevels(1)&&xpPoints(1)"}) {
            assertTrue(expression, LotteryPoolValidator.validate(pool("", expression))
                    .contains("invalid_voucher_expression"));
        }
    }

    @Test
    public void copiesMetadataWithoutSharingRewards() {
        LotteryPool original = pool("Line one\nLine two", "xpPoints(2)||score(\"coins\",3)");
        LotteryPool copy = LotteryPools.copy(original);
        assertEquals(GSON.toJson(original), GSON.toJson(copy));
        assertEquals("Line one\nLine two", GSON.toJsonTree(copy).getAsJsonObject()
                .get("description").getAsString());
        assertEquals("xpPoints(2)||score(\"coins\",3)", GSON.toJsonTree(copy).getAsJsonObject()
                .get("voucherExpression").getAsString());
        assertNotSame(original.getRewards(), copy.getRewards());
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
