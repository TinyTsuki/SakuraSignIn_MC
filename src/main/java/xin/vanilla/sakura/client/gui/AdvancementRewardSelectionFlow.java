package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import xin.vanilla.banira.client.gui.AdvancementSelectScreen;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 将 Banira 进度选择器适配为奖励领域值编辑器。
 */
public final class AdvancementRewardSelectionFlow {
    private AdvancementRewardSelectionFlow() {
    }

    public static Screen create(
            Screen parent,
            Identifier defaultAdvancement,
            Consumer<Identifier> onSelected
    ) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(defaultAdvancement);
        Objects.requireNonNull(onSelected);

        AdvancementSelectScreen.Args args = new AdvancementSelectScreen.Args()
                .parentScreen(parent)
                .defaultAdvancement(defaultAdvancement)
                .closeAfterSubmit(true)
                .onDataReceived((Consumer<Identifier>) advancement ->
                        onSelected.accept(copyValue(advancement)));
        return new AdvancementSelectScreen(args);
    }

    static Identifier copyValue(Identifier advancement) {
        return Identifier.parse(advancement.toString());
    }
}
