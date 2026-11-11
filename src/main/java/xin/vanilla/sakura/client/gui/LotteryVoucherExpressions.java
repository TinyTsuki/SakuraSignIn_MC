package xin.vanilla.sakura.client.gui;

import com.google.gson.Gson;
import net.minecraft.world.item.ItemStack;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.common.util.ItemUtils;

public final class LotteryVoucherExpressions {
    private static final Gson GSON = new Gson();

    private LotteryVoucherExpressions() {
    }

    static final class Fields {
        final String kind, specification;
        final int amount;

        Fields(String kind, String specification, int amount) {
            this.kind = kind;
            this.specification = specification;
            this.amount = amount;
        }

        boolean same(Fields other) {
            return kind.equals(other.kind) && specification.equals(other.specification) && amount == other.amount;
        }
    }

    static Fields read(String expression) {
        try {
            xin.vanilla.banira.api.cost.CostRequirement parsed = BaniraCosts.parse(expression);
            if (parsed.kind() == xin.vanilla.banira.api.cost.CostRequirement.Kind.FREE)
                return new Fields("free", "", 1);
            if (parsed.kind() == xin.vanilla.banira.api.cost.CostRequirement.Kind.LEAF) {
                xin.vanilla.banira.api.cost.ResourceCost cost = parsed.resource();
                int amount = cost.amount();
                if (amount > 0) {
                    switch (cost.resourceId()) {
                        case "banira:item":
                            if (cost.matchMode() == xin.vanilla.banira.api.cost.ResourceCost.MatchMode.ID)
                                return new Fields("item", cost.specification(), amount);
                            break;
                        case "banira:score":
                            return new Fields("score", cost.specification(), amount);
                        case "banira:xp_point":
                            return new Fields("xpPoints", "", amount);
                        case "banira:xp_level":
                            return new Fields("xpLevels", "", amount);
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the original expression editable, including invalid legacy input.
        }
        return new Fields("advanced", "", 1);
    }

    static String build(String kind, String specification, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Invalid voucher amount");
        String expression;
        switch (kind) {
            case "item":
            case "score":
                expression = kind + "(" + GSON.toJson(specification.trim()) + "," + amount + ")";
                break;
            case "xpPoints":
            case "xpLevels":
                expression = kind + "(" + amount + ")";
                break;
            default:
                throw new IllegalArgumentException("Unknown voucher type");
        }
        BaniraCosts.parse(expression);
        return expression;
    }

    public static String item(ItemStack stack, int amount, boolean exact) {
        net.minecraft.core.HolderLookup.Provider registries = exact
                ? java.util.Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().level, "Client world required").registryAccess()
                : null;
        return item(stack, amount, exact, registries);
    }

    public static String item(ItemStack stack, int amount, boolean exact, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || amount < 1) {
            throw new IllegalArgumentException("Invalid item voucher");
        }
        String specification = exact ? new net.minecraft.commands.arguments.item.ItemInput(stack.getItemHolder(),
                stack.getComponentsPatch()).serialize(java.util.Objects.requireNonNull(registries, "registries"))
                : ItemUtils.getItemRegistry(stack.getItem()).toString();
        String expression = (exact ? "itemExact(" : "item(")
                + GSON.toJson(specification) + "," + amount + ")";
        BaniraCosts.parse(expression);
        return expression;
    }
}
