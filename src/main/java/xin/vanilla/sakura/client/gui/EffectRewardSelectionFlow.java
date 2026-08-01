package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.potion.EffectInstance;
import xin.vanilla.banira.client.gui.EffectSelectScreen;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 将 Banira 效果选择器适配为奖励领域值编辑器。
 */
public final class EffectRewardSelectionFlow {
    private static final int DEFAULT_DURATION_TICKS = 600;
    private EffectRewardSelectionFlow() {
    }

    public static Screen create(
            Screen parent,
            EffectInstance defaultEffect,
            Consumer<EffectInstance> onSelected
    ) {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(defaultEffect);
        Objects.requireNonNull(onSelected);

        EffectSelectScreen.Args args = new EffectSelectScreen.Args()
                .parentScreen(parent)
                .defaultEffect(copyValue(defaultEffect))
                .closeAfterSubmit(true)
                .onDataReceived((Consumer<EffectInstance>) effect -> onSelected.accept(copyValue(effect)));
        return new EffectSelectScreen(args);
    }

    static EffectInstance copyValue(EffectInstance effect) {
        int duration = effect.getDuration() > 0
                ? effect.getDuration() : DEFAULT_DURATION_TICKS;
        return new EffectInstance(effect.getEffect(), duration, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }
}
