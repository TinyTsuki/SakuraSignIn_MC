package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import xin.vanilla.banira.client.gui.ItemSelectScreen;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 将 Banira 物品选择器与 Sakura 奖励概率组合为一个编辑流程。
 */
public final class ItemRewardSelectionFlow {
    private ItemRewardSelectionFlow() {
    }

    public static Screen create(
            Screen parent,
            Reward defaultReward,
            Consumer<Reward> onSelected
    ) {
        return create(parent, defaultReward, null, onSelected);
    }

    public static Screen create(
            Screen parent,
            Reward defaultReward,
            @Nullable Supplier<Boolean> shouldClose,
            Consumer<Reward> onSelected
    ) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(defaultReward);
        Objects.requireNonNull(onSelected);

        ItemStack defaultItem = RewardManager.deserializeReward(defaultReward);
        ItemSelectScreen.Args args = new ItemSelectScreen.Args()
                .parentScreen(parent)
                .defaultItem(defaultItem)
                .shouldClose(shouldClose)
                .closeAfterSubmit(false)
                .onDataReceived((Consumer<ItemStack>) itemStack -> Minecraft.getInstance().setScreen(
                        RewardProbabilityFlow.create(
                                parent,
                                defaultReward.getProbability(),
                                probability -> toReward(itemStack, probability),
                                onSelected
                        )
                ));
        return new ItemSelectScreen(args);
    }

    static Reward toReward(ItemStack itemStack, BigDecimal probability) {
        return new Reward(itemStack.copy(), ERewardType.ITEM, probability);
    }
}
