package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
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
            ResourceLocation defaultAdvancement,
            Consumer<ResourceLocation> onSelected
    ) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(defaultAdvancement);
        Objects.requireNonNull(onSelected);

        AdvancementSelectScreen.Args args = new AdvancementSelectScreen.Args()
                .parentScreen(parent)
                .defaultAdvancement(defaultAdvancement)
                .closeAfterSubmit(true)
                .onDataReceived((Consumer<ResourceLocation>) advancement ->
                        onSelected.accept(copyValue(advancement)));
        return new AdvancementSelectScreen(args);
    }

    static ResourceLocation copyValue(ResourceLocation advancement) {
        return new ResourceLocation(advancement.toString());
    }
}
