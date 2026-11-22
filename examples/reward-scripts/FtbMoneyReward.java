package xin.vanilla.banira.generated;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.script.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * FTB Money 1605.1.4. The core mod has no dependency on FTB Money.
 */
public final class FtbMoneyReward extends JsonRewardScript {
    private static final RewardTypeId ID = RewardTypeId.of("example", "ftb_money");

    public ScriptRewardDescriptor descriptor() {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("amount", 100);
        return new ScriptRewardDescriptor(ID, "FTB Money", "minecraft:gold_nugget", defaults,
                Collections.singletonList(ScriptRewardField.integer("amount", "金币数量", 1, Long.MAX_VALUE)));
    }

    private static long amount(JsonObject data) {
        return data.get("amount").getAsBigDecimal().longValueExact();
    }

    public RewardGrantResult grant(RewardGrantContext context, JsonObject data) {
        try {
            Object player = context.player();
            Class<?> api = Class.forName("dev.ftb.mods.ftbmoney.FTBMoney", true,
                    RewardScript.class.getClassLoader());
            Method read = null;
            Method add = null;
            // Match the server-player overload without relying on Minecraft mapping names.
            for (Method method : api.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length > 0 && types[0].isInstance(player)) {
                    if (method.getName().equals("getMoney") && types.length == 1
                            && method.getReturnType() == long.class) read = method;
                    if (method.getName().equals("addMoney") && types.length == 2
                            && types[1] == long.class) add = method;
                }
            }
            if (read == null || add == null)
                return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "ftbmoney_api");
            long value = amount(data);
            Math.addExact(((Long) read.invoke(null, player)).longValue(), value);
            add.invoke(null, player, Long.valueOf(value));
            return RewardGrantResult.success();
        } catch (ClassNotFoundException failure) {
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "ftbmoney_missing");
        } catch (LinkageError failure) {
            return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, "ftbmoney_dependency_missing");
        } catch (Exception failure) {
            return RewardGrantResult.of(RewardGrantStatus.FAILED, failure.getClass().getSimpleName());
        }
    }

    public List<RewardViolation> validate(JsonObject value) {
        try {
            if (value.get("amount").getAsJsonPrimitive().isNumber() && amount(value) > 0)
                return Collections.emptyList();
        } catch (RuntimeException invalid) {
        }
        return Collections.singletonList(new RewardViolation("amount", "positive_integer"));
    }

    public xin.vanilla.banira.common.data.Component describe(String language, JsonObject value, boolean withAmount) {
        return SakuraComponent.get().literal("FTB Money" + (withAmount ? " +" + amount(value) : ""));
    }

    public RewardAddPermission permission() {
        return RewardAddPermission.of(2, "sakura_sign_in:reward.add.example_ftb_money");
    }
}
