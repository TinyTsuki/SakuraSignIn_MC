package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import xin.vanilla.banira.client.gui.ItemSelectScreen;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 将 Banira 物品选择器适配为奖励领域值编辑器。
 */
public final class ItemRewardSelectionFlow {
    private ItemRewardSelectionFlow() {
    }

    public static Screen create(
            Screen parent,
            ItemStack defaultItem,
            Consumer<ItemStack> onSelected
    ) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(defaultItem);
        Objects.requireNonNull(onSelected);

        ItemSelectScreen.Args args = new ItemSelectScreen.Args()
                .parentScreen(parent)
                .defaultItem(defaultItem.copy())
                .closeAfterSubmit(true)
                .onDataReceived((Consumer<ItemStack>) itemStack -> onSelected.accept(itemStack.copy()));
        return new ItemSelectScreen(args);
    }
}
