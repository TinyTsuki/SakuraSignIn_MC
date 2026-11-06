package xin.vanilla.sakura.client.gui;

import com.google.gson.Gson;
import net.minecraft.world.item.ItemStack;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.common.util.ItemUtils;

public final class LotteryVoucherExpressions {
    private static final Gson GSON = new Gson();

    private LotteryVoucherExpressions() {
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
