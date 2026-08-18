package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.NumberUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.reward.client.RewardEditContext;
import xin.vanilla.sakura.api.reward.client.RewardEditorProvider;
import xin.vanilla.sakura.api.reward.client.StandardRewardEditors;
import xin.vanilla.sakura.client.gui.AdvancementRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.EffectRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.ItemRewardSelectionFlow;
import xin.vanilla.sakura.data.collection.StringList;
import xin.vanilla.sakura.screen.StringInputScreen;

/** 内置类型的界面适配，不参与公共奖励执行。 */
final class BuiltInRewardEditors {
    private BuiltInRewardEditors() {
    }

    static RewardEditorProvider<ItemStack> item() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                ItemRewardSelectionFlow.create(parent(context),
                        context.getInitialValue() == null
                                ? new ItemStack(Items.AIR) : context.getInitialValue(),
                        context.getSubmit())));
    }

    static RewardEditorProvider<MobEffectInstance> effect() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                EffectRewardSelectionFlow.create(parent(context),
                        context.getInitialValue() == null
                                ? new MobEffectInstance(MobEffects.LUCK, 600, 0)
                                : context.getInitialValue(),
                        context.getSubmit())));
    }

    static RewardEditorProvider<ResourceLocation> advancement() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                AdvancementRewardSelectionFlow.create(parent(context),
                        context.getInitialValue() == null
                                ? ResourceLocation.parse("minecraft:story/root")
                                : context.getInitialValue(),
                        context.getSubmit())));
    }

    static RewardEditorProvider<Integer> positiveInteger(String titleKey) {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                new StringInputScreen(parent(context),
                        title(titleKey), hint(), "\\d*",
                        String.valueOf(context.getInitialValue() == null
                                ? 1 : context.getInitialValue()),
                        values -> {
                            StringList errors = new StringList();
                            int value = NumberUtils.toInt(values.get(0));
                            if (value > 0) {
                                context.getSubmit().accept(value);
                            } else {
                                errors.add(SakuraComponent.get().transClient(
                                        "format", "enter_value_s_error", values.get(0)).toString());
                            }
                            return errors;
                        })));
    }

    static RewardEditorProvider<Component> message() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                new StringInputScreen(parent(context), title("enter_message"), hint(), "",
                        context.getInitialValue() == null ? "" : context.getInitialValue().toString(),
                        (java.util.function.Consumer<StringList>) values -> context.getSubmit().accept(
                                SakuraComponent.get().literal(values.get(0))))));
    }

    static RewardEditorProvider<String> command() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                new StringInputScreen(parent(context), title("enter_command"), hint(), "",
                        context.getInitialValue() == null ? "" : context.getInitialValue(),
                        values -> {
                            StringList errors = new StringList();
                            if (values.get(0).startsWith("/")) {
                                context.getSubmit().accept(values.get(0));
                            } else {
                                errors.add(SakuraComponent.get().transClient(
                                        "format", "enter_value_s_error", values.get(0)).toString());
                            }
                            return errors;
                        })));
    }

    private static Screen parent(RewardEditContext<?> context) {
        if (!(context.getParentScreen() instanceof Screen)) {
            throw new IllegalArgumentException("Reward editor parent must be a Screen");
        }
        return (Screen) context.getParentScreen();
    }

    private static Text title(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static Text hint() {
        return title("enter_something");
    }
}
