package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.potion.EffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.banira.client.gui.EffectSelectScreen;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 将 Banira 效果选择器与 Sakura 奖励概率组合为一个编辑流程。
 */
@OnlyIn(Dist.CLIENT)
public final class EffectRewardSelectionFlow {
    private EffectRewardSelectionFlow() {
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

        EffectInstance defaultEffect = RewardManager.deserializeReward(defaultReward);
        EffectSelectScreen.Args args = new EffectSelectScreen.Args()
                .parentScreen(parent)
                .defaultEffect(defaultEffect)
                .shouldClose(shouldClose)
                .closeAfterSubmit(false)
                .onDataReceived((Consumer<EffectInstance>) effect -> Minecraft.getInstance().setScreen(
                        RewardProbabilityFlow.create(
                                parent,
                                defaultReward.getProbability(),
                                probability -> toReward(effect, probability),
                                onSelected
                        )
                ));
        return new EffectSelectScreen(args);
    }

    static Reward toReward(EffectInstance effect, BigDecimal probability) {
        return new Reward(effect, ERewardType.EFFECT, probability);
    }
}
