package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.sakura.reward.builtin.CommandRewardData;
import xin.vanilla.sakura.reward.builtin.CommandRewardCodec;
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

/**
 * 内置类型的界面适配，不参与公共奖励执行。
 */
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

    static RewardEditorProvider<Identifier> advancement() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                AdvancementRewardSelectionFlow.create(parent(context),
                        context.getInitialValue() == null
                                ? Identifier.parse("minecraft:story/root")
                                : context.getInitialValue(),
                        context.getSubmit())));
    }

    static RewardEditorProvider<Integer> positiveInteger(String titleKey) {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                positiveIntegerScreen(context, titleKey)));
    }

    static Screen positiveIntegerScreen(RewardEditContext<Integer> context, String titleKey) {
        return new StringInputScreen(parent(context),
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
                });
    }

    static RewardEditorProvider<Component> message() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                messageScreen(context)));
    }

    static Screen messageScreen(RewardEditContext<Component> context) {
        return new StringInputScreen(parent(context), title("enter_message"), hint(), "",
                context.getInitialValue() == null ? "" : context.getInitialValue().toString(),
                (java.util.function.Consumer<StringList>) values -> context.getSubmit().accept(
                        SakuraComponent.get().literal(values.get(0))));
    }

    static RewardEditorProvider<CommandRewardData> command() {
        return StandardRewardEditors.custom(context -> Minecraft.getInstance().setScreen(
                commandScreen(context)));
    }

    static Screen commandScreen(RewardEditContext<CommandRewardData> context) {
        CommandRewardData initial = context.getInitialValue() == null
                ? new CommandRewardData("", "", "") : context.getInitialValue();
        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen(parent(context))
                .setHeaderTitle(title("enter_command"));
        args.addWidget(new InputFormScreen.Widget().name("command").title(title("enter_command"))
                .maxLength(32767).defaultValue(initial.command()));
        args.addWidget(new InputFormScreen.Widget().name("displayName").title(title("reward_display_name"))
                .allowEmpty(true).maxLength(128).defaultValue(initial.displayName()));
        java.util.concurrent.atomic.AtomicReference<String> selectedIcon = new java.util.concurrent.atomic.AtomicReference<>(initial.displayIcon());
        args.addWidget(new InputFormScreen.Widget().name("displayIcon").title(title("reward_display_icon"))
                .allowEmpty(true).maxLength(256).defaultValue(initial.displayIcon())
                .type(InputFormScreen.WidgetType.PICKER)
                .picker((screen, selected) -> ItemRewardSelectionFlow.create(screen,
                        xin.vanilla.banira.common.util.ItemUtils.deserializeItemStack(selectedIcon.get().isEmpty()
                                ? "minecraft:repeating_command_block" : selectedIcon.get()),
                        stack -> {
                            String icon = stack.isEmpty() ? "" : xin.vanilla.banira.common.util.ItemUtils.getItemRegistry(stack.getItem()).toString();
                            selectedIcon.set(icon);
                            selected.accept(icon);
                        })));
        args.setCallback(results -> {
            CommandRewardData value = new CommandRewardData(results.value("command"),
                    results.value("displayName"), results.value("displayIcon"));
            try {
                new CommandRewardCodec().encode(value);
                context.getSubmit().accept(value);
            } catch (xin.vanilla.sakura.api.reward.RewardDataException failure) {
                results.runningResult(SakuraComponent.get().transClient("word", "input_format_invalid").toString());
            }
        });
        return new InputFormScreen(args);
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
