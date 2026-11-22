package xin.vanilla.sakura.client.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.ItemUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.client.*;
import xin.vanilla.sakura.api.reward.script.ScriptRewardDescriptor;
import xin.vanilla.sakura.api.reward.script.ScriptRewardField;
import xin.vanilla.sakura.api.reward.script.ScriptRewardForm;
import xin.vanilla.sakura.api.reward.script.JsonRewardCodec;
import xin.vanilla.banira.client.gui.widget.DropdownOption;

import java.util.*;

public final class ScriptRewardClient {
    private ScriptRewardClient() {
    }

    public static void install(List<ScriptRewardDescriptor> descriptors) {
        Map<RewardTypeId, RewardClientExtension<JsonObject>> extensions = new LinkedHashMap<>();
        for (ScriptRewardDescriptor descriptor : descriptors) {
            RewardPresentation<JsonObject> presentation = new RewardPresentation<JsonObject>() {
                public Component displayName(RewardDisplayContext context, JsonObject value) {
                    String amount = descriptor.amount(value);
                    return SakuraComponent.get().literal(descriptor.name()
                            + (context.withAmount() && !amount.isEmpty() ? " x" + amount : ""));
                }

                public void renderIcon(RewardRenderContext context, JsonObject value) {
                    ItemStack icon = ItemUtils.deserializeItemStack(descriptor.icon());
                    context.drawItem(icon.isEmpty() ? new ItemStack(Items.PAPER) : icon);
                    context.drawAmount(descriptor.amount(value));
                }
            };
            RewardClientExtension<JsonObject> extension = RewardClientExtension.builder(presentation)
                    .typeName(() -> SakuraComponent.get().literal(descriptor.name())).sortOrder(1000)
                    .editor(context -> {
                        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen((Screen) context.getParentScreen())
                                .setHeaderTitle(Text.from(SakuraComponent.get().literal(descriptor.name())));
                        ScriptRewardForm form = new ScriptRewardForm(descriptor, context.getInitialValue());
                        Map<String, String> names = new LinkedHashMap<>();
                        for (ScriptRewardField field : form.fields()) {
                            String name = "field_" + names.size();
                            names.put(field.key(), name);
                            String label = field.label();
                            if (label.equals(field.key()) && field.key().equals("amount"))
                                label = tr("script_reward_amount");
                            if (field.type() == ScriptRewardField.Type.JSON) label += " (JSON)";
                            InputFormScreen.Widget widget = new InputFormScreen.Widget().name(name)
                                    .title(Text.from(SakuraComponent.get().literal(label)))
                                    .maxLength(field.type() == ScriptRewardField.Type.INTEGER || field.type() == ScriptRewardField.Type.DECIMAL ? 128 : 16384)
                                    .allowEmpty(field.type() == ScriptRewardField.Type.TEXT)
                                    .wideInput(field.type() == ScriptRewardField.Type.TEXT || field.type() == ScriptRewardField.Type.JSON)
                                    .defaultValue(form.value(field))
                                    .validator(result -> {
                                        try {
                                            field.parse(result.value());
                                            return "";
                                        } catch (RuntimeException invalid) {
                                            return tr("input_format_invalid");
                                        }
                                    });
                            if (field.type() == ScriptRewardField.Type.BOOLEAN) {
                                widget.type(InputFormScreen.WidgetType.DROPDOWN).dropdownOptionEntries(Arrays.asList(
                                        new DropdownOption("true", tr("script_reward_enabled")),
                                        new DropdownOption("false", tr("script_reward_disabled"))));
                            } else if (field.type() == ScriptRewardField.Type.INTEGER || field.type() == ScriptRewardField.Type.DECIMAL) {
                                String hint = tr(field.type() == ScriptRewardField.Type.INTEGER ? "script_reward_integer" : "script_reward_decimal");
                                if (!field.minimum().isEmpty() || !field.maximum().isEmpty())
                                    hint += " [" + field.minimum() + ", " + field.maximum() + "]";
                                widget.tooltip(Text.from(SakuraComponent.get().literal(hint)));
                            }
                            args.addWidget(widget);
                        }
                        if (form.fields().isEmpty()) args.addWidget(new InputFormScreen.Widget().name("data")
                                .title(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.script_reward_data"))
                                .wideInput(true).maxLength(16384).defaultValue((context.getInitialValue() == null
                                        ? descriptor.defaults() : context.getInitialValue()).toString()));
                        args.setCallback(results -> {
                            JsonObject value;
                            try {
                                value = form.fields().isEmpty() ? new JsonRewardCodec().decode(
                                        new JsonParser().parse(results.value("data")).getAsJsonObject())
                                        : form.submit(key -> results.value(names.get(key)));
                            } catch (RuntimeException | xin.vanilla.sakura.api.reward.RewardDataException failure) {
                                results.runningResult(tr("input_format_invalid"));
                                return;
                            }
                            context.getSubmit().accept(value);
                        });
                        Minecraft.getInstance().setScreen(new InputFormScreen(args));
                    }).build();
            if (extensions.put(descriptor.id(), extension) != null)
                throw new IllegalArgumentException("Duplicate script reward descriptor");
        }
        SakuraRewardClient.replaceRemoteScripts(extensions);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
