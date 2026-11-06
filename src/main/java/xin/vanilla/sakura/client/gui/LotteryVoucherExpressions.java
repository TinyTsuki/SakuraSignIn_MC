package xin.vanilla.sakura.client.gui;

import com.google.gson.Gson;
import net.minecraft.world.item.ItemStack;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.api.cost.CostRequirement;
import xin.vanilla.banira.api.cost.ResourceCost;
import xin.vanilla.banira.common.util.ItemUtils;

public final class LotteryVoucherExpressions {
    private static final Gson GSON = new Gson();

    private LotteryVoucherExpressions() {
    }

    record Fields(String kind, String item, String objective, int amount) {
    }

    static Fields read(String expression) {
        try {
            var cost = BaniraCosts.parse(expression);
            if (cost.kind() == CostRequirement.Kind.FREE) return new Fields("free", "", "", 1);
            if (cost.kind() == CostRequirement.Kind.LEAF) {
                var resource = cost.resource();
                int amount = resource.amount();
                String spec = resource.specification();
                if (amount > 0) switch (resource.resourceId()) {
                    case "banira:item":
                        if (resource.matchMode() == ResourceCost.MatchMode.ID)
                            return new Fields("item", spec, "", amount);
                        break;
                    case "banira:xp_point":
                        return new Fields("xpPoints", "", "", amount);
                    case "banira:xp_level":
                        return new Fields("xpLevels", "", "", amount);
                    case "banira:score":
                        return new Fields("score", "", spec, amount);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Keep unsupported or invalid legacy values available for manual correction.
        }
        return new Fields("advanced", "", "", 1);
    }

    static String build(String kind, String item, String objective, int amount) {
        if (amount < 1) throw new IllegalArgumentException("Invalid voucher amount");
        String expression = switch (kind) {
            case "item" -> "item(" + GSON.toJson(item.trim()) + "," + amount + ")";
            case "xpPoints", "xpLevels" -> kind + "(" + amount + ")";
            case "score" -> "score(" + GSON.toJson(objective.trim()) + "," + amount + ")";
            default -> throw new IllegalArgumentException("Unknown voucher type");
        };
        BaniraCosts.parse(expression);
        return expression;
    }

    public static String item(ItemStack stack, int amount, boolean exact) {
        if (stack == null || stack.isEmpty() || amount < 1) {
            throw new IllegalArgumentException("Invalid item voucher");
        }
        String specification = exact ? ItemUtils.serializeItemStack(stack)
                : ItemUtils.getItemRegistry(stack.getItem()).toString();
        String expression = (exact ? "itemExact(" : "item(")
                + GSON.toJson(specification) + "," + amount + ")";
        BaniraCosts.parse(expression);
        return expression;
    }
}
