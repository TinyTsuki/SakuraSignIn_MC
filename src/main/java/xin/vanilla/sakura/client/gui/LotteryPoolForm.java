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
        ArtworkDraft artwork = new ArtworkDraft(existing);
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
                .picker(LotteryPoolForm::voucherPicker)
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
        args.addWidget(artworkField("poster", "lottery_poster_png", artwork, false));
        args.addWidget(artworkField("thumbnail", "lottery_thumbnail_png", artwork, true));
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
            candidate.setThumbnailPng(artwork.thumbnail);
            candidate.setPosterPng(artwork.poster);
            candidate.setArtworkItem(artwork.item);
            candidate.setArtworkTexture(artwork.texture);
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
                .maxLength(16).allowEmpty(true).defaultValue(value)
                .validator(result -> LotteryPoolValidator.validDate(result.value().trim()) ? "" : tr("lottery_date_invalid"));
    }

    private static Screen voucherPicker(Screen parent, Consumer<String> selected) {
        var args = new InputFormScreen.Args().setParentScreen(parent)
                .setHeaderTitle(text("lottery_voucher_builder"));
        args.addWidget(dropdown("kind", "lottery_voucher_source", "item",
                option("item", "lottery_voucher_item"), option("xpPoints", "lottery_voucher_points"),
                option("xpLevels", "lottery_voucher_levels"), option("score", "lottery_voucher_score")));
        args.addWidget(itemField("item", "lottery_voucher_item", ""));
        args.addWidget(new InputFormScreen.Widget().name("objective").title(text("lottery_voucher_objective"))
                .allowEmpty(true).maxLength(128));
        args.addWidget(number("amount", "lottery_voucher_amount", "lottery_number_range", 1, 1, 999_999));
        args.setCallback(results -> {
            try {
                selected.accept(LotteryVoucherExpressions.build(results.value("kind"), results.value("item"),
                        results.value("objective"), Integer.parseInt(results.value("amount"))));
            } catch (IllegalArgumentException failure) {
                results.runningResult(tr("lottery_voucher_incomplete"));
            }
        });
        return new InputFormScreen(args);
    }

    private static InputFormScreen.Widget itemField(String name, String title, String initial) {
        return new InputFormScreen.Widget().name(name).title(text(title))
                .type(InputFormScreen.WidgetType.PICKER).allowEmpty(true)
                .maxLength(LotteryPoolValidator.MAX_ARTWORK_ID).defaultValue(initial)
                .picker((screen, selected) -> ItemRewardSelectionFlow.create(screen, net.minecraft.world.item.ItemStack.EMPTY,
                        stack -> selected.accept(stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(stack.getItem()).toString())));
    }

    private static InputFormScreen.Widget artworkField(String name, String title, ArtworkDraft artwork, boolean thumbnail) {
        return new InputFormScreen.Widget().name(name).title(text(title))
                .type(InputFormScreen.WidgetType.PICKER).pickerReadOnly(true)
                .defaultValue(artwork.summary(thumbnail))
                .picker((screen, selected) -> artworkPicker(screen, title, artwork, thumbnail, selected));
    }

    private static Screen artworkPicker(Screen parent, String title, ArtworkDraft artwork,
                                        boolean thumbnail, Consumer<String> selected) {
        var args = new InputFormScreen.Args().setParentScreen(parent).setHeaderTitle(text(title));
        var modes = new java.util.ArrayList<DropdownOption>();
        modes.add(option("keep", "lottery_png_keep"));
        modes.add(option("png", "lottery_artwork_file"));
        if (!thumbnail) {
            modes.add(option("item", "lottery_artwork_item"));
            modes.add(option("texture", "lottery_artwork_texture"));
        }
        modes.add(option("clear", thumbnail ? "lottery_artwork_auto" : "lottery_png_remove"));
        args.addWidget(dropdown("mode", "lottery_artwork_source", "keep", modes.toArray(DropdownOption[]::new)));
        args.addWidget(new InputFormScreen.Widget().name("png").title(text("lottery_artwork_file"))
                .tooltip(text("lottery_png_hint")).type(InputFormScreen.WidgetType.FILE)
                .fileFilter("png").allowEmpty(true).maxLength(2048));
        if (!thumbnail) {
            args.addWidget(itemField("item", "lottery_artwork_item", artwork.item));
            args.addWidget(new InputFormScreen.Widget().name("texture").title(text("lottery_artwork_texture"))
                    .tooltip(text("lottery_artwork_texture_hint")).allowEmpty(true)
                    .maxLength(LotteryPoolValidator.MAX_ARTWORK_ID).defaultValue(artwork.texture));
        }
        args.setCallback(results -> {
            String mode = results.value("mode");
            if (mode.equals("keep")) {
                if (!results.value("png").isBlank()) mode = "png";
                else if (!thumbnail && !results.value("item").equals(artwork.item)) mode = "item";
                else if (!thumbnail && !results.value("texture").equals(artwork.texture)) mode = "texture";
            }
            try {
                switch (mode) {
                    case "png" -> {
                        String png = readImage(results.value("png"));
                        if (thumbnail) artwork.thumbnail = png;
                        else artwork.main("", "", png);
                    }
                    case "item" -> {
                        String item = results.value("item");
                        if (item.isBlank() || !LotteryPoolValidator.isArtworkItem(item)) {
                            results.runningResult(tr("input_format_invalid"));
                            return;
                        }
                        artwork.main(item, "", "");
                    }
                    case "texture" -> {
                        String texture = results.value("texture");
                        if (texture.isBlank() || !LotteryPoolValidator.isArtworkTexture(texture)) {
                            results.runningResult(tr("input_format_invalid"));
                            return;
                        }
                        artwork.main("", texture, "");
                    }
                    case "clear" -> {
                        if (thumbnail) artwork.thumbnail = "";
                        else artwork.main("", "", "");
                    }
                    default -> { }
                }
                selected.accept(artwork.summary(thumbnail));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                results.runningResult(tr("lottery_png_invalid"));
            }
        });
        return new InputFormScreen(args);
    }

    private static String readImage(String path) throws java.io.IOException {
        if (path.isBlank()) throw new IllegalArgumentException("Missing PNG path");
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
            byte[] bytes = input.readNBytes(xin.vanilla.sakura.data.lottery.LotteryPng.MAX_BYTES + 1);
            xin.vanilla.sakura.data.lottery.LotteryPng.inspect(bytes);
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
    }

    private static final class ArtworkDraft {
        private String item, texture, poster, thumbnail;

        private ArtworkDraft(LotteryPool pool) {
            main(pool == null ? "" : pool.getArtworkItem(), pool == null ? "" : pool.getArtworkTexture(),
                    pool == null ? "" : pool.getPosterPng());
            thumbnail = pool == null ? "" : pool.getThumbnailPng();
        }

        private void main(String item, String texture, String png) {
            this.item = item;
            this.texture = texture;
            this.poster = png;
        }

        private String summary(boolean thumbnail) {
            if (thumbnail) return tr(this.thumbnail.isEmpty() ? "lottery_artwork_auto" : "lottery_artwork_custom");
            if (!poster.isEmpty()) return tr("lottery_artwork_custom");
            if (!texture.isEmpty()) return texture;
            if (!item.isEmpty()) return item;
            return tr("lottery_artwork_none");
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
