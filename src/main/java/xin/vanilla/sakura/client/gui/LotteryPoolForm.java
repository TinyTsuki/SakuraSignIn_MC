package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screen.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPoolValidator;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 奖池规则使用明确选项配置，避免玩家猜测策略枚举与数值含义。
 */
public final class LotteryPoolForm {
    private LotteryPoolForm() {
    }

    public static Screen create(Screen parent, LotteryPool existing,
                                Consumer<LotteryPool> submit) {
        Consumer<LotteryPool> connectedSubmit = connectedOnce(submit);
        InputFormScreen.Args args = new InputFormScreen.Args()
                .setParentScreen(parent)
                .setHeaderTitle(text("lottery_pool_title"));
        args.addWidget(new InputFormScreen.Widget().name("id")
                .title(text("lottery_pool_id")).hint(text("lottery_pool_id_hint"))
                .tooltip(text("lottery_pool_id_hint"))
                .maxLength(64)
                .regex("[a-z0-9_.-]{1,64}")
                .defaultValue(existing == null ? "" : existing.getId())
                .validator(result -> {
                    String id = result.value();
                    if (id == null || !id.matches("[a-z0-9_.-]{1,64}")) {
                        return tr("input_format_invalid");
                    }
                    if (existing != null) {
                        return existing.getId().equals(id) ? "" : tr("lottery_pool_id_locked");
                    }
                    boolean duplicate = RewardConfigManager.getRewardConfig().getLotteryPools()
                            .stream().anyMatch(pool -> id.equals(pool.getId()));
                    return duplicate ? tr("lottery_pool_duplicate") : "";
                }));
        args.addWidget(new InputFormScreen.Widget().name("name")
                .title(text("lottery_pool_name")).hint(text("lottery_pool_name_hint"))
                .tooltip(text("lottery_pool_name_hint"))
                .maxLength(96)
                .regex(".{1,96}")
                .defaultValue(existing == null ? "" : existing.getDisplayName())
                .validator(result -> {
                    String name = result.value();
                    return name == null || name.trim().isEmpty() || name.length() > 96
                            ? tr("input_format_invalid") : "";
                }));
        args.addWidget(new InputFormScreen.Widget().name("description")
                .title(text("lottery_pool_description")).hint(text("lottery_pool_description"))
                .tooltip(text("lottery_pool_description"))
                .maxLength(LotteryPoolValidator.MAX_DESCRIPTION).allowEmpty(true)
                .regex("[\\s\\S]{0,2048}")
                .defaultValue(existing == null ? "" : existing.getDescription()));
        VoucherDraft voucher = new VoucherDraft(existing == null ? "" : existing.getVoucherExpression());
        args.addWidget(new InputFormScreen.Widget().name("voucherExpression")
                .title(text("lottery_cost_label")).type(InputFormScreen.WidgetType.PICKER)
                .pickerReadOnly(true).defaultValue(voucher.summary())
                .picker((screen, selected) -> voucherPicker(screen, voucher, selected))
                .validator(result -> voucherError(voucher.expression)));
        args.addWidget(dropdown("policy", "lottery_limit_policy",
                existing == null ? LotteryLimitPolicy.DAILY.name()
                        : existing.getLimitPolicy().name(),
                Arrays.stream(LotteryLimitPolicy.values()).map(policy -> option(
                                policy.name(), "lottery_policy_" + policy.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        args.addWidget(number("maxDraws", "lottery_max_draws",
                "lottery_max_draws_tooltip",
                existing == null ? 1 : existing.getMaxDraws(), 1, 10_000));
        args.addWidget(number("cooldown", "lottery_cooldown_seconds",
                "lottery_cooldown_tooltip",
                existing == null ? 0 : existing.getCooldownSeconds(), 0, 31_536_000));
        args.addWidget(dropdown("previewMode", "lottery_preview_mode",
                (existing == null ? LotteryPreviewMode.ALL : existing.getPreviewMode()).name(),
                Arrays.stream(LotteryPreviewMode.values()).map(mode -> option(
                                mode.name(), "lottery_preview_" + mode.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        args.setCallback(results -> {
            LotteryPool candidate = new LotteryPool(results.value("id"), results.value("name"),
                    LotteryLimitPolicy.valueOf(results.value("policy")),
                    Integer.parseInt(results.value("maxDraws")),
                    Integer.parseInt(results.value("cooldown")),
                    LotteryPreviewMode.valueOf(results.value("previewMode")),
                    existing == null ? new RewardList() : existing.getRewards());
            candidate.setDescription(results.value("description"));
            candidate.setVoucherExpression(voucher.expression);
            if (!LotteryPoolValidator.validate(candidate).isEmpty()) {
                results.runningResult(tr("input_format_invalid"));
                return;
            }
            connectedSubmit.accept(candidate);
        });
        return new InputFormScreen(args);
    }

    private static Screen voucherPicker(Screen parent, VoucherDraft voucher, Consumer<String> selected) {
        LotteryVoucherExpressions.Fields current = LotteryVoucherExpressions.read(voucher.expression);
        Consumer<String> save = connectedOnce(expression -> {
            voucher.expression = expression;
            selected.accept(voucher.summary());
        });
        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen(parent).setHeaderTitle(text("lottery_cost_label"));
        args.addWidget(dropdown("kind", "lottery_voucher_source", current.kind,
                option("free", "lottery_free"), option("item", "lottery_voucher_item"),
                option("xpPoints", "lottery_voucher_points"), option("xpLevels", "lottery_voucher_levels"),
                option("score", "lottery_voucher_score"), option("advanced", "lottery_voucher_advanced")));
        String item = "item".equals(current.kind) ? current.specification : "";
        java.util.concurrent.atomic.AtomicReference<String> selectedItem = new java.util.concurrent.atomic.AtomicReference<>(item);
        args.addWidget(new InputFormScreen.Widget().name("item").title(text("lottery_voucher_item"))
                .type(InputFormScreen.WidgetType.PICKER).pickerReadOnly(true).defaultValue(item)
                .picker((screen, callback) -> ItemRewardSelectionFlow.create(screen, selectedItem.get().isEmpty() ? net.minecraft.item.ItemStack.EMPTY
                        : xin.vanilla.banira.common.util.ItemUtils.deserializeItemStack(selectedItem.get()), stack -> {
                    if (stack.isEmpty()) return;
                    String id = xin.vanilla.banira.common.util.ItemUtils.getItemRegistry(stack.getItem()).toString();
                    selectedItem.set(id);
                    callback.accept(id);
                }, false))
                .visibleWhen(result -> "item".equals(result.value("kind"))));
        args.addWidget(new InputFormScreen.Widget().name("objective").title(text("lottery_voucher_objective"))
                .maxLength(128).defaultValue("score".equals(current.kind) ? current.specification : "")
                .visibleWhen(result -> "score".equals(result.value("kind"))));
        args.addWidget(number("amount", "lottery_voucher_amount", "lottery_number_range", current.amount, 1, Integer.MAX_VALUE)
                .regex("[0-9]{1,10}").visibleWhen(result -> {
                    String kind = result.value("kind");
                    return "item".equals(kind) || "xpPoints".equals(kind) || "xpLevels".equals(kind) || "score".equals(kind);
                }));
        args.addWidget(new InputFormScreen.Widget().name("advanced").title(text("lottery_voucher_expression"))
                .tooltip(text("lottery_voucher_advanced_tooltip"))
                .maxLength(LotteryPoolValidator.MAX_VOUCHER_EXPRESSION).allowEmpty(true).defaultValue(voucher.expression)
                .validator(result -> voucherError(result.value()))
                .visibleWhen(result -> "advanced".equals(result.value("kind"))));
        args.setCallback(results -> {
            try {
                String kind = results.value("kind");
                String expression;
                if ("free".equals(kind)) expression = "";
                else if ("advanced".equals(kind)) expression = results.value("advanced");
                else expression = LotteryVoucherExpressions.build(kind, results.value("score".equals(kind) ? "objective" : "item"),
                        Integer.parseInt(results.value("amount")));
                String error = voucherError(expression);
                if (!error.isEmpty()) {
                    results.runningResult(error);
                    return;
                }
                if (!"advanced".equals(kind) && LotteryVoucherExpressions.read(expression).same(current))
                    expression = voucher.expression;
                save.accept(expression);
            } catch (IllegalArgumentException invalid) {
                results.runningResult(tr("input_format_invalid"));
            }
        });
        return new InputFormScreen(args);
    }

    private static String voucherError(String expression) {
        try {
            xin.vanilla.banira.api.BaniraCosts.parse(expression);
            return "";
        } catch (xin.vanilla.banira.api.cost.CostExpressionException failure) {
            return SakuraComponent.get().transClient("format", "lottery_expression_error_ss",
                    failure.line(), failure.column()).toString();
        } catch (IllegalArgumentException failure) {
            return tr("lottery_expression_invalid");
        }
    }

    private static final class VoucherDraft {
        private String expression;

        VoucherDraft(String expression) {
            this.expression = expression;
        }

        String summary() {
            LotteryVoucherExpressions.Fields cost = LotteryVoucherExpressions.read(expression);
            String amount = " x" + cost.amount;
            switch (cost.kind) {
                case "free": return tr("lottery_free");
                case "item": return cost.specification + amount;
                case "score": return tr("lottery_voucher_score") + ": " + cost.specification + amount;
                case "xpPoints": return tr("lottery_voucher_points") + amount;
                case "xpLevels": return tr("lottery_voucher_levels") + amount;
                default: return tr("lottery_voucher_advanced");
            }
        }
    }

    private static <T> Consumer<T> connectedOnce(Consumer<T> callback) {
        lombok.var client = net.minecraft.client.Minecraft.getInstance();
        Object origin = client == null ? null : client.getConnection();
        lombok.var submitted = new java.util.concurrent.atomic.AtomicBoolean();
        return value -> {
            lombok.var current = net.minecraft.client.Minecraft.getInstance();
            if (origin == (current == null ? null : current.getConnection()) && submitted.compareAndSet(false, true))
                callback.accept(value);
        };
    }

    private static InputFormScreen.Widget number(String name, String titleKey,
                                                 String hintKey, int value,
                                                 int min, int max) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .tooltip(text(hintKey)).maxLength(String.valueOf(max).length())
                .regex("[0-9]{1,8}")
                .defaultValue(String.valueOf(value))
                .validator(result -> {
                    try {
                        int parsed = Integer.parseInt(result.value());
                        return parsed >= min && parsed <= max ? "" : tr("lottery_number_range");
                    } catch (NumberFormatException ignored) {
                        return tr("lottery_number_range");
                    }
                });
    }

    private static InputFormScreen.Widget dropdown(String name, String titleKey,
                                                   String value, DropdownOption... options) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .type(InputFormScreen.WidgetType.DROPDOWN)
                .dropdownOptionEntries(Arrays.asList(options)).defaultValue(value);
    }

    private static DropdownOption option(String value, String key) {
        String label = tr(key);
        String description = tr(key + "_tooltip");
        return new DropdownOption(value, label, net.minecraft.item.ItemStack.EMPTY, null,
                SakuraComponent.get().literal(description.endsWith("." + key + "_tooltip") ? label : description));
    }

    private static Text text(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
