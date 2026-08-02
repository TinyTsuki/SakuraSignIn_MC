package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screen.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.common.util.NumberUtils;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.common.data.ScopedComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.banira.common.util.StringUtils;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 统一奖励概率的输入、校验与 Banira 文本解析。
 */
public final class RewardProbabilityFlow {
    private static final ScopedComponent COMPONENTS = new ScopedComponent(SakuraSignIn.MODID);

    private RewardProbabilityFlow() {
    }

    public static Screen create(
            Screen parent,
            BigDecimal defaultProbability,
            Function<BigDecimal, Reward> rewardFactory,
            Consumer<Reward> onSelected
    ) {
        InputFormScreen.Widget probability = new InputFormScreen.Widget()
                .title(Text.literal(translation("enter_reward_probability")))
                .tooltip(Text.literal(translation("reward_probability_tooltip")))
                .maxLength(16)
                .regex(RewardProbabilityInput.PERCENT_REGEX)
                .defaultValue(RewardProbabilityInput.display(defaultProbability))
                .validator(result -> {
                    if (RewardProbabilityInput.isValidPercent(result.value())) {
                        return null;
                    }
                    return translation("reward_probability_s_error", result.value());
                });
        InputFormScreen.Args args = new InputFormScreen.Args()
                .setParentScreen(parent)
                .addWidget(probability)
                .setCallback(result -> onSelected.accept(
                        rewardFactory.apply(RewardProbabilityInput.parse(result.firstValue()))
                ));
        return new InputFormScreen(args);
    }

    private static String translation(String key, Object... args) {
        String type = args.length == 0 ? "word" : "format";
        return COMPONENTS.transClient(type + "." + SakuraSignIn.MODID + "." + key, args).toString();
    }
}
