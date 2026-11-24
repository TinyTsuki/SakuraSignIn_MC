package xin.vanilla.banira.generated;

import com.google.gson.JsonObject;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.RewardAddPermission;
import xin.vanilla.sakura.api.reward.RewardGrantContext;
import xin.vanilla.sakura.api.reward.RewardGrantResult;
import xin.vanilla.sakura.api.reward.RewardGrantStatus;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.RewardViolation;
import xin.vanilla.sakura.api.reward.script.JsonRewardScript;
import xin.vanilla.sakura.api.reward.script.RewardScript;
import xin.vanilla.sakura.api.reward.script.ScriptRewardDescriptor;
import xin.vanilla.sakura.api.reward.script.ScriptRewardField;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class VaultEconomyReward extends JsonRewardScript {
    private static final RewardTypeId ID = RewardTypeId.of("example", "vault_economy");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    public ScriptRewardDescriptor descriptor() {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("amount", 100);
        return new ScriptRewardDescriptor(ID, "Vault Currency", "minecraft:gold_nugget", defaults,
                Collections.singletonList(ScriptRewardField.decimal("amount", "\u8d27\u5e01\u6570\u91cf", "0.01", "1000000000")));
    }

    private static BigDecimal amount(JsonObject data) {
        if (data == null || !data.has("amount") || !data.get("amount").isJsonPrimitive()
                || !data.getAsJsonPrimitive("amount").isNumber()) throw new IllegalArgumentException("amount");
        BigDecimal value = data.get("amount").getAsBigDecimal();
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("amount");
        value = value.stripTrailingZeros();
        if (value.scale() > 2) throw new IllegalArgumentException("amount");
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
        BigDecimal value;
        try {
            value = amount(data);
        } catch (RuntimeException invalid) {
            return RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT, "amount");
        }
        UUID playerId = context == null ? null : context.playerId();
        if (playerId == null) return RewardGrantResult.of(RewardGrantStatus.FAILED, "missing_player_uuid");

        boolean depositStarted = false;
        try {
            Class<?> bukkit = bukkitApi();
            if (bukkit.getMethod("getServer").invoke(null) == null) return unavailable("bukkit_not_running");
            if (!Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null)))
                return RewardGrantResult.of(RewardGrantStatus.FAILED, "not_bukkit_primary_thread");

            Method servicesGetter = bukkit.getMethod("getServicesManager");
            Class<?> servicesApi = servicesGetter.getReturnType();
            Object services = servicesGetter.invoke(null);
            if (services == null) return unavailable("vault_economy_unavailable");
            Class<?> economy = economyApi(servicesApi, services);
            if (economy == null) return unavailable("vault_economy_unavailable_or_ambiguous");
            Object provider = servicesApi.getMethod("load", Class.class).invoke(services, economy);
            if (!economy.isInstance(provider) || !Boolean.TRUE.equals(economy.getMethod("isEnabled").invoke(provider)))
                return unavailable("vault_economy_provider_unavailable");

            int digits = ((Integer) economy.getMethod("fractionalDigits").invoke(provider)).intValue();
            if (digits >= 0 && value.scale() > digits)
                return RewardGrantResult.of(RewardGrantStatus.INVALID_CONTENT, "vault_economy_precision");
            Object player = bukkit.getMethod("getPlayer", UUID.class).invoke(null, playerId);
            if (player == null) return RewardGrantResult.of(RewardGrantStatus.FAILED, "player_not_online_in_bukkit");
            Class<?> offlinePlayer = bukkit.getMethod("getOfflinePlayer", UUID.class).getReturnType();
            Method deposit = economy.getMethod("depositPlayer", offlinePlayer, double.class);
            Class<?> responseApi = deposit.getReturnType();
            Method success = responseApi.getMethod("transactionSuccess");
            double requested = value.doubleValue();

            depositStarted = true;
            Object response = deposit.invoke(provider, player, Double.valueOf(requested));
            if (response == null) return uncertain();
            if (!Boolean.TRUE.equals(success.invoke(response)))
                return RewardGrantResult.of(RewardGrantStatus.FAILED, "vault_economy_rejected");
            return RewardGrantResult.success();
        } catch (ClassNotFoundException missing) {
            return unavailable("bukkit_missing");
        } catch (NoSuchMethodException incompatible) {
            return unavailable("bukkit_or_vault_api_incompatible");
        } catch (LinkageError incompatible) {
            return depositStarted ? uncertain() : unavailable("bukkit_or_vault_api_incompatible");
        } catch (Exception failure) {
            return depositStarted ? uncertain() : RewardGrantResult.of(RewardGrantStatus.FAILED, "vault_economy_lookup_failed");
        }
    }

    private static Class<?> bukkitApi() throws ClassNotFoundException {
        String name = "org.bukkit.Bukkit";
        ClassLoader modLoader = RewardScript.class.getClassLoader();
        try {
            return Class.forName(name, false, modLoader);
        } catch (ClassNotFoundException missing) {
            ClassLoader serverLoader = Thread.currentThread().getContextClassLoader();
            if (serverLoader == null || serverLoader == modLoader) throw missing;
            return Class.forName(name, false, serverLoader);
        }
    }

    private static Class<?> economyApi(Class<?> servicesApi, Object services) throws Exception {
        Class<?> match = null;
        // Use Bukkit's registered Class so Vault can live in a separate plugin class loader.
        Collection<?> types = (Collection<?>) servicesApi.getMethod("getKnownServices").invoke(services);
        for (Object entry : types) {
            Class<?> type = (Class<?>) entry;
            if (!"net.milkbowl.vault.economy.Economy".equals(type.getName())) continue;
            if (match != null && match != type) return null;
            match = type;
        }
        return match;
    }

    private static RewardGrantResult unavailable(String reason) {
        return RewardGrantResult.of(RewardGrantStatus.TYPE_UNAVAILABLE, reason);
    }

    private static RewardGrantResult uncertain() {
        return RewardGrantResult.of(RewardGrantStatus.FAILED, "vault_deposit_unconfirmed_check_balance_before_retry");
    }

    public Component describe(String language, JsonObject data, boolean withAmount) {
        String name = language != null && language.startsWith("zh") ? "Vault \u8d27\u5e01" : "Vault Currency";
        if (withAmount) {
            try {
                name += " +" + amount(data).toPlainString();
            } catch (RuntimeException invalid) {
                // Keep malformed reward data displayable without showing a misleading amount.
            }
        }
        return SakuraComponent.get().literal(name);
    }

    public RewardAddPermission permission() {
        return RewardAddPermission.of(2, "sakura_sign_in:reward.add.example_vault_economy");
    }
}
