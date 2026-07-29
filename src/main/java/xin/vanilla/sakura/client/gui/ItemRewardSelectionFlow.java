package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.ItemSelectScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.StringUtils;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 将 Banira 物品选择器与 Sakura 奖励概率组合为一个编辑流程。
 */
@OnlyIn(Dist.CLIENT)
public final class ItemRewardSelectionFlow {
    private static final String PROBABILITY_REGEX = "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?";

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
                        probabilityScreen(parent, itemStack, defaultReward.getProbability(), onSelected)
                ));
        return new ItemSelectScreen(args);
    }

    static Reward toReward(ItemStack itemStack, BigDecimal probability) {
        return new Reward(itemStack.copy(), ERewardType.ITEM, probability);
    }

    private static Screen probabilityScreen(
            Screen parent,
            ItemStack itemStack,
            BigDecimal defaultProbability,
            Consumer<Reward> onSelected
    ) {
        InputFormScreen.Widget probability = new InputFormScreen.Widget()
                .title(Text.literal(translation("enter_reward_probability")))
                .regex(PROBABILITY_REGEX)
                .defaultValue(StringUtils.toFixedEx(defaultProbability, 5))
                .validator(result -> {
                    BigDecimal value = StringUtils.toBigDecimal(result.value());
                    if (isProbability(value)) {
                        return null;
                    }
                    return translation("reward_probability_s_error", result.value());
                });
        InputFormScreen.Args args = new InputFormScreen.Args()
                .setParentScreen(parent)
                .addWidget(probability)
                .setCallback(result -> onSelected.accept(
                        toReward(itemStack, StringUtils.toBigDecimal(result.firstValue()))
                ));
        return new InputFormScreen(args);
    }

    private static boolean isProbability(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static String translation(String key, Object... args) {
        return Component.translatableClient(EI18nType.TIPS, key, args).toString();
    }
}
