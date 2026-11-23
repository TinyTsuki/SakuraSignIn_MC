package xin.vanilla.sakura.reward.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.script.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ViScriptShopRewardExampleTest {
    private JsonRewardScript compile() throws Exception {
        String source = new String(Files.readAllBytes(ExampleScriptFiles.resolve("ViScriptShopReward.java")), StandardCharsets.UTF_8);
        try (ScriptSession<RewardScript> session = BaniraScripts.open("example-test", RewardScript.class, "1",
                new ScriptLimits(262144, 1048576, 32), Runnable::run)) {
            return (JsonRewardScript) session.prepare(Collections.singletonList(new ScriptSource("vss",
                            "xin.vanilla.banira.generated.ViScriptShopReward", "ViScriptShopReward.java", source)))
                    .get(30, TimeUnit.SECONDS).scripts().get("vss");
        }
    }

    private JsonObject data(String number) {
        JsonObject value = new JsonObject();
        value.add("amount", new JsonParser().parse(number));
        return value;
    }

    private RewardGrantResult grant(JsonRewardScript script, Wallet wallet, Class<?> api, String number) throws Exception {
        Method method = script.getClass().getDeclaredMethod("grantTo", Object.class, JsonObject.class, Class.class);
        method.setAccessible(true);
        return (RewardGrantResult) method.invoke(script, wallet, data(number), api);
    }

    @Test
    public void compilesWithVisualFieldLocalizedNameAndAmountBadge() throws Exception {
        JsonRewardScript script = compile();
        ScriptRewardDescriptor descriptor = script.descriptor();
        assertEquals("example:viscript_shop_money", descriptor.id().toString());
        assertEquals(1, descriptor.fields().size());
        assertEquals("amount", descriptor.fields().get(0).key());
        assertEquals("12.5", descriptor.amount(data("12.50")));
        assertEquals("ViScriptShop \u91d1\u5e01", script.describe("zh_cn", data("12.5"), false).getString("zh_cn"));
        assertEquals("ViScriptShop Coins +12.5", script.describe("en_us", data("12.5"), true).getString("en_us"));
    }

    @Test
    public void validatesDecimalsAndRejectsStringsFractionsAndExtremeNumbers() throws Exception {
        JsonRewardScript script = compile();
        for (String value : new String[]{"0.01", "12.50", "100", "1000000000"})
            assertTrue(value, script.validate(data(value)).isEmpty());
        for (String value : new String[]{"0", "-1", "0.001", "1000000001", "1e999999999", "\"100\"", "null", "true", "[]"})
            assertFalse(value, script.validate(data(value)).isEmpty());
        assertFalse(script.validate(new JsonObject()).isEmpty());
        assertFalse(script.validate(null).isEmpty());
    }

    @Test
    public void missingModAndInvalidContentDoNotBecomeSuccessfulRewards() throws Exception {
        JsonRewardScript script = compile();
        assertEquals(RewardGrantStatus.TYPE_UNAVAILABLE, script.grant(null, data("100")).getStatus());
        assertEquals(RewardGrantStatus.INVALID_CONTENT, script.grant(null, data("0")).getStatus());
    }

    @Test
    public void addsMoneyOnceUsingThePublicApiAndPreservesNegativeBalances() throws Exception {
        JsonRewardScript script = compile();
        Wallet wallet = new Wallet();
        wallet.balance = -10;
        assertTrue(grant(script, wallet, MoneyApi.class, "12.50").isSuccess());
        assertEquals(2.5, wallet.balance, 0);
        assertEquals(1, wallet.writes);
        wallet.balance = 0.1;
        assertTrue(grant(script, wallet, MoneyApi.class, "0.20").isSuccess());
        assertEquals(2, wallet.writes);
    }

    @Test
    public void rejectsChangedApiAndPrecisionLossBeforeMutation() throws Exception {
        JsonRewardScript script = compile();
        Wallet wallet = new Wallet();
        assertEquals(RewardGrantStatus.TYPE_UNAVAILABLE, grant(script, wallet, String.class, "100").getStatus());
        wallet.balance = 1e16;
        assertEquals(RewardGrantStatus.FAILED, grant(script, wallet, MoneyApi.class, "0.01").getStatus());
        wallet.balance = Double.NaN;
        assertEquals(RewardGrantStatus.FAILED, grant(script, wallet, MoneyApi.class, "1").getStatus());
        assertEquals(0, wallet.writes);
    }

    @Test
    public void detectsExternalCapsWithoutRetryingTheWrite() throws Exception {
        JsonRewardScript script = compile();
        Wallet wallet = new Wallet();
        assertEquals(RewardGrantStatus.FAILED, grant(script, wallet, CappedApi.class, "100").getStatus());
        assertEquals(50, wallet.balance, 0);
        assertEquals(1, wallet.writes);
        Wallet large = new Wallet();
        large.balance = 1e14;
        assertEquals(RewardGrantStatus.FAILED, grant(script, large, HalfCreditApi.class, "0.06").getStatus());
        assertEquals(1, large.writes);
    }

    public static final class Wallet {
        double balance;
        int writes;
    }

    public static class MoneyApi {
        public static double getMoney(Wallet player) {
            return player.balance;
        }

        public static void addMoney(Wallet player, double amount) {
            player.balance += amount;
            player.writes++;
        }
    }

    public static final class CappedApi {
        public static double getMoney(Wallet player) {
            return player.balance;
        }

        public static void addMoney(Wallet player, double amount) {
            player.balance = Math.min(50, player.balance + amount);
            player.writes++;
        }
    }

    public static final class HalfCreditApi {
        public static double getMoney(Wallet player) {
            return player.balance;
        }

        public static void addMoney(Wallet player, double amount) {
            player.balance += amount / 2;
            player.writes++;
        }
    }
}
