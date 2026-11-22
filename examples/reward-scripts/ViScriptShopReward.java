package xin.vanilla.banira.generated;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.script.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * ViScriptShop 1.2.5: Forge 1.20.1 and NeoForge 1.21.1.
 * Install only on a server with the matching ViScriptShop and its dependencies.
 */
public final class ViScriptShopReward extends JsonRewardScript {
    private static final RewardTypeId ID = RewardTypeId.of("example", "viscript_shop_money");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    public ScriptRewardDescriptor descriptor() {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("amount", 100);
        return new ScriptRewardDescriptor(ID, "ViScriptShop Coins", "minecraft:gold_nugget", defaults,
                Collections.singletonList(ScriptRewardField.decimal("amount", "\u91d1\u5e01\u6570\u91cf", "0.01", "1000000000")));
    }

    private static BigDecimal amount(JsonObject data) {
        if (data == null || !data.has("amount") || !data.get("amount").isJsonPrimitive()
                || !data.getAsJsonPrimitive("amount").isNumber()) throw new IllegalArgumentException("amount");
        BigDecimal value = data.get("amount").getAsBigDecimal().stripTrailingZeros();
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0 || value.scale() > 2)
            throw new IllegalArgumentException("amount");
        return value;
    }

    public List<RewardViolation> validate(JsonObject data) {
        try {
            amount(data);
            return Collections.emptyList();
        } catch (RuntimeException invalid) {
            return Collections.singletonList(new RewardViolation("amount", "positive_amount_max_1b_two_decimals"));
        }
    }

    public RewardGrantResult grant(RewardGrantContext context, JsonObject data) {
        if (!validate(data).isEmpty()) return RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT, "amount");
        try {
            Class<?> api = Class.forName("com.viscriptshop.util.ViScriptShopServerUtil", true,
                    RewardScript.class.getClassLoader());
            return grantTo(context == null ? null : context.player(), data, api);
        } catch (ClassNotFoundException unavailable) {
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "viscript_shop_missing");
        } catch (LinkageError unavailable) {
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "viscript_shop_dependency_missing");
        } catch (Exception failure) {
            return RewardGrantResult.of(RewardGrantStatus.FAILED, failure.getClass().getSimpleName());
        }
    }

    private static RewardGrantResult grantTo(Object player, JsonObject data, Class<?> api) throws Exception {
        if (player == null) return RewardGrantResult.of(RewardGrantStatus.FAILED, "missing_server_player");
        Method read = null;
        Method add = null;
        // Match public server-player overloads without Minecraft mapping names.
        for (Method method : api.getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers()) || types.length == 0 || !types[0].isInstance(player))
                continue;
            if (method.getName().equals("getMoney") && types.length == 1 && method.getReturnType() == double.class) {
                if (read != null)
                    return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "ambiguous_money_api");
                read = method;
            }
            if (method.getName().equals("addMoney") && types.length == 2 && types[1] == double.class
                    && method.getReturnType() == void.class) {
                if (add != null) return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "ambiguous_money_api");
                add = method;
            }
        }
        if (read == null || add == null || read.getParameterTypes()[0] != add.getParameterTypes()[0])
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "viscript_shop_api");
        BigDecimal value = amount(data);
        double before = ((Double) read.invoke(null, player)).doubleValue();
        if (!Double.isFinite(before)) return RewardGrantResult.of(RewardGrantStatus.FAILED, "invalid_balance");
        BigDecimal sum = BigDecimal.valueOf(before).add(value);
        double expected = sum.doubleValue();
        // Refuse amounts lost to the target's double precision before any mutation.
        if (!Double.isFinite(expected) || expected <= before || BigDecimal.valueOf(expected).compareTo(sum) != 0)
            return RewardGrantResult.of(RewardGrantStatus.FAILED, "balance_precision_limit");
        add.invoke(null, player, Double.valueOf(value.doubleValue()));
        double after = ((Double) read.invoke(null, player)).doubleValue();
        double tolerance = Math.min(Math.ulp(expected) * 2, value.doubleValue() * 1.0E-9);
        if (!Double.isFinite(after) || after <= before || Math.abs(after - expected) > tolerance)
            return RewardGrantResult.of(RewardGrantStatus.FAILED, "balance_not_confirmed_do_not_auto_retry");
        return RewardGrantResult.success();
    }

    public xin.vanilla.banira.common.data.Component describe(String language, JsonObject data, boolean withAmount) {
        String name = language != null && language.startsWith("zh") ? "ViScriptShop \u91d1\u5e01" : "ViScriptShop Coins";
        return SakuraComponent.get().literal(name + (withAmount ? " +" + amount(data).toPlainString() : ""));
    }

    public RewardAddPermission permission() {
        return RewardAddPermission.of(2, "sakura_sign_in:reward.add.example_viscript_shop_money");
    }
}
