package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.client.gui.AdvancementSelectScreen;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 将 Banira 进度选择器与 Sakura 奖励概率组合为一个编辑流程。
 */
public final class AdvancementRewardSelectionFlow {
    private AdvancementRewardSelectionFlow() {
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

        ResourceLocation defaultAdvancement = RewardManager.deserializeReward(defaultReward);
        AdvancementSelectScreen.Args args = new AdvancementSelectScreen.Args()
                .parentScreen(parent)
                .defaultAdvancement(defaultAdvancement)
                .shouldClose(shouldClose)
                .closeAfterSubmit(false)
                .onDataReceived((Consumer<ResourceLocation>) advancement -> Minecraft.getInstance().setScreen(
                        RewardProbabilityFlow.create(
                                parent,
                                defaultReward.getProbability(),
                                probability -> toReward(advancement, probability),
                                onSelected
                        )
                ));
        return new AdvancementSelectScreen(args);
    }

    static Reward toReward(ResourceLocation advancement, BigDecimal probability) {
        return new Reward(advancement, ERewardType.ADVANCEMENT, probability);
    }
}
