package xin.vanilla.sakura.client.gui;

import com.google.gson.Gson;
import net.minecraft.world.item.ItemStack;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.common.util.ItemUtils;

public final class LotteryVoucherExpressions {
    private static final Gson GSON = new Gson();

    private LotteryVoucherExpressions() {
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
