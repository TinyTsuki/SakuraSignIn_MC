package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
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
        args.addWidget(new InputFormScreen.Widget().name("voucherExpression")
                .title(text("lottery_voucher_expression")).hint(text("lottery_voucher_expression"))
                .tooltip(text("lottery_voucher_expression_hint"))
                .type(InputFormScreen.WidgetType.PICKER)
                .picker((screen, selected) -> ItemRewardSelectionFlow.create(screen,
                        net.minecraft.world.item.ItemStack.EMPTY, stack ->
                                selected.accept(LotteryVoucherExpressions.item(stack, Math.max(1, stack.getCount()), false))))
                .maxLength(LotteryPoolValidator.MAX_VOUCHER_EXPRESSION).allowEmpty(true)
                .defaultValue(existing == null ? "" : existing.getVoucherExpression())
                .validator(result -> {
                    try {
                        xin.vanilla.banira.api.BaniraCosts.parse(result.value());
                        return "";
                    } catch (xin.vanilla.banira.api.cost.CostExpressionException failure) {
                        return SakuraComponent.get().transClient("format", "lottery_expression_error_ss",
                                failure.line(), failure.column()).toString();
                    } catch (IllegalArgumentException failure) {
                        return tr("lottery_expression_invalid");
                    }
                }));
        args.addWidget(dropdown("policy", "lottery_limit_policy",
                existing == null ? LotteryLimitPolicy.DAILY.name()
                        : existing.getLimitPolicy().name(),
                Arrays.stream(LotteryLimitPolicy.values()).map(policy -> option(
                                policy.name(), "lottery_policy_" + policy.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        args.addWidget(number("maxDraws", "lottery_max_draws",
                "lottery_max_draws_tooltip",
                existing == null ? 10 : existing.getMaxDraws(), 1, 10_000));
        args.addWidget(number("cooldown", "lottery_cooldown_seconds",
                "lottery_cooldown_tooltip",
                existing == null ? 0 : existing.getCooldownSeconds(), 0, 31_536_000));
        args.addWidget(dropdown("previewMode", "lottery_preview_mode",
                (existing == null ? LotteryPreviewMode.ALL : existing.getPreviewMode()).name(),
                Arrays.stream(LotteryPreviewMode.values()).map(mode -> option(
                                mode.name(), "lottery_preview_" + mode.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        args.addWidget(dropdown("animationStyle", "lottery_animation",
                (existing == null ? xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.STRIP : existing.getAnimationStyle()).name(),
                Arrays.stream(xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.values()).map(style -> option(
                                style.name(), "enum_lottery_animation_style_" + style.name().toLowerCase(java.util.Locale.ROOT)))
                        .toArray(DropdownOption[]::new)));
        args.addWidget(new InputFormScreen.Widget().name("drawCounts").title(text("lottery_batch_count"))
                .hint(text("lottery_draw_counts_hint")).tooltip(text("lottery_draw_counts_hint")).maxLength(64)
                .defaultValue(existing == null ? "1,10" : existing.getDrawCounts().stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                .validator(result -> {
                    try {
                        LotteryPoolValidator.parseDrawCounts(result.value());
                        return "";
                    } catch (IllegalArgumentException failure) {
                        return tr("lottery_draw_counts_invalid");
                    }
                }));
        args.addWidget(date("startDate", "lottery_start_date", existing == null ? "" : existing.getStartDate()));
        args.addWidget(date("endDate", "lottery_end_date", existing == null ? "" : existing.getEndDate()));
        args.addWidget(new InputFormScreen.Widget().name("artworkItem").title(text("lottery_artwork_item"))
                .type(InputFormScreen.WidgetType.PICKER).allowEmpty(true).maxLength(LotteryPoolValidator.MAX_ARTWORK_ID)
                .defaultValue(existing == null ? "" : existing.getArtworkItem())
                .picker((screen, selected) -> ItemRewardSelectionFlow.create(screen, net.minecraft.world.item.ItemStack.EMPTY,
                        stack -> selected.accept(stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())))
                .validator(result -> LotteryPoolValidator.isArtworkItem(result.value()) ? "" : tr("input_format_invalid")));
        args.addWidget(new InputFormScreen.Widget().name("artworkTexture").title(text("lottery_artwork_texture"))
                .tooltip(text("lottery_artwork_texture_hint")).allowEmpty(true).maxLength(LotteryPoolValidator.MAX_ARTWORK_ID)
                .defaultValue(existing == null ? "" : existing.getArtworkTexture())
                .validator(result -> LotteryPoolValidator.isArtworkTexture(result.value()) ? "" : tr("input_format_invalid")));
        addImageField(args, "thumbnail", "lottery_thumbnail_png", existing == null ? "" : existing.getThumbnailPng());
        addImageField(args, "poster", "lottery_poster_png", existing == null ? "" : existing.getPosterPng());
        args.setCallback(results -> {
            LotteryPool candidate = new LotteryPool(results.value("id"), results.value("name"),
                    LotteryLimitPolicy.valueOf(results.value("policy")),
                    Integer.parseInt(results.value("maxDraws")),
                    Integer.parseInt(results.value("cooldown")),
                    LotteryPreviewMode.valueOf(results.value("previewMode")),
                    existing == null ? new RewardList() : existing.getRewards());
            candidate.setDescription(results.value("description"));
            candidate.setVoucherExpression(results.value("voucherExpression"));
            candidate.setAnimationStyle(xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.valueOf(results.value("animationStyle")));
            candidate.setDrawCounts(LotteryPoolValidator.parseDrawCounts(results.value("drawCounts")));
            candidate.setStartDate(results.value("startDate").trim());
            candidate.setEndDate(results.value("endDate").trim());
            try {
                candidate.setThumbnailPng(image(results, "thumbnail", existing == null ? "" : existing.getThumbnailPng()));
                candidate.setPosterPng(image(results, "poster", existing == null ? "" : existing.getPosterPng()));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                results.runningResult(tr("lottery_png_invalid"));
                return;
            }
            candidate.setArtworkItem(results.value("artworkItem"));
            candidate.setArtworkTexture(results.value("artworkTexture"));
            var errors = LotteryPoolValidator.validate(candidate);
            if (errors.contains("invalid_draw_modes")) {
                results.runningResult(tr("lottery_batch_rule_invalid"));
                return;
            }
            if (errors.contains("invalid_dates")) {
                results.runningResult(tr("lottery_date_invalid"));
                return;
            }
            if (!errors.isEmpty()) {
                results.runningResult(tr("input_format_invalid"));
                return;
            }
            connectedSubmit.accept(candidate);
        });
        return new InputFormScreen(args);
    }

    private static InputFormScreen.Widget date(String name, String title, String value) {
        return new InputFormScreen.Widget().name(name).title(text(title)).tooltip(text("lottery_date_hint"))
                .maxLength(10).allowEmpty(true).defaultValue(value)
                .validator(result -> LotteryPoolValidator.validDate(result.value().trim()) ? "" : tr("lottery_date_invalid"));
    }

    private static void addImageField(InputFormScreen.Args args, String name, String title, String current) {
        args.addWidget(new InputFormScreen.Widget().name(name).title(text(title)).tooltip(text("lottery_png_hint"))
                .type(InputFormScreen.WidgetType.FILE).fileFilter("png").allowEmpty(true).maxLength(2048).defaultValue(""));
        if (!current.isEmpty()) args.addWidget(dropdown(name + "Existing", title, "keep",
                option("keep", "lottery_png_keep"), option("remove", "lottery_png_remove")));
    }

    private static String image(InputFormScreen.Results values, String name, String current) throws java.io.IOException {
        String path = values.value(name);
        if (path.isBlank())
            return !current.isEmpty() && "remove".equals(values.value(name + "Existing")) ? "" : current;
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
            byte[] bytes = input.readNBytes(xin.vanilla.sakura.data.lottery.LotteryPng.MAX_BYTES + 1);
            xin.vanilla.sakura.data.lottery.LotteryPng.inspect(bytes);
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
    }

    private static <T> Consumer<T> connectedOnce(Consumer<T> callback) {
        var client = net.minecraft.client.Minecraft.getInstance();
        Object origin = client == null ? null : client.getConnection();
        var submitted = new java.util.concurrent.atomic.AtomicBoolean();
        return value -> {
            var current = net.minecraft.client.Minecraft.getInstance();
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
        return new DropdownOption(value, tr(key), net.minecraft.world.item.ItemStack.EMPTY,
                null, SakuraComponent.get().transClient("word", key + "_tooltip"));
    }

    private static Text text(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
