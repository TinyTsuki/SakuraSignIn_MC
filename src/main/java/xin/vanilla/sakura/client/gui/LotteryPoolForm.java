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
                existing == null ? 10 : existing.getMaxDraws(), 1, 10_000));
        args.addWidget(number("cooldown", "lottery_cooldown_seconds",
                "lottery_cooldown_tooltip",
                existing == null ? 0 : existing.getCooldownSeconds(), 0, 31_536_000));
        args.addWidget(dropdown("previewMode", "lottery_preview_mode",
                (existing == null ? LotteryPreviewMode.ALL : existing.getPreviewMode()).name(),
                Arrays.stream(LotteryPreviewMode.values()).map(mode -> option(
                                mode.name(), "lottery_preview_" + mode.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        ArtworkDraft artwork = new ArtworkDraft(existing);
        args.addWidget(new InputFormScreen.Widget().name("drawCounts").title(text("lottery_batch_count"))
                .tooltip(text("lottery_draw_sizes_hint")).maxLength(64).allowEmpty(true)
                .defaultValue(existing == null ? "1,10" : existing.getDrawCounts() == null ? ""
                        : existing.getDrawCounts().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                .validator(result -> {
                    try {
                        if (!result.value().trim().isEmpty()) LotteryPoolValidator.parseDrawCounts(result.value());
                        return "";
                    } catch (IllegalArgumentException failure) {
                        return tr("lottery_draw_sizes_invalid");
                    }
                }));
        args.addWidget(date("startDate", "lottery_start_date", existing == null ? "" : existing.getStartDate()));
        args.addWidget(date("endDate", "lottery_end_date", existing == null ? "" : existing.getEndDate()));
        args.addWidget(artworkField("poster", "lottery_poster_png", artwork, false));
        args.addWidget(artworkField("thumbnail", "lottery_thumbnail_png", artwork, true));
        java.util.List<DropdownOption> animations = new java.util.ArrayList<DropdownOption>();
        animations.add(option("legacy", "lottery_animation_legacy"));
        for (xin.vanilla.sakura.data.lottery.LotteryAnimationStyle animation : xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.values()) {
            animations.add(new DropdownOption(animation.name(), animation.enumDescription().toString()));
        }
        args.addWidget(dropdown("animationStyle", "lottery_animation", existing == null ? "STRIP"
                        : existing.getAnimationStyle() == null ? "legacy" : existing.getAnimationStyle().name(),
                animations.toArray(new DropdownOption[0])));
        args.setCallback(results -> {
            LotteryPool candidate = new LotteryPool(results.value("id"), results.value("name"),
                    LotteryLimitPolicy.valueOf(results.value("policy")),
                    Integer.parseInt(results.value("maxDraws")),
                    Integer.parseInt(results.value("cooldown")),
                    LotteryPreviewMode.valueOf(results.value("previewMode")),
                    existing == null ? new RewardList() : existing.getRewards());
            candidate.setDescription(results.value("description"));
            candidate.setVoucherExpression(voucher.expression);
            String counts = java.util.Objects.toString(results.value("drawCounts"), "");
            if (!counts.trim().isEmpty()) {
                try {
                    candidate.setDrawCounts(LotteryPoolValidator.parseDrawCounts(counts));
                } catch (IllegalArgumentException failure) {
                    results.runningResult(tr("lottery_draw_sizes_invalid"));
                    return;
                }
            }
            candidate.setStartDate(java.util.Objects.toString(results.value("startDate"), "").trim());
            candidate.setEndDate(java.util.Objects.toString(results.value("endDate"), "").trim());
            candidate.setThumbnailPng(artwork.thumbnail);
            candidate.setPosterPng(artwork.poster);
            candidate.setArtworkItem(artwork.item);
            candidate.setArtworkTexture(artwork.texture);
            String animation = results.value("animationStyle");
            if (animation != null && !"legacy".equals(animation))
                candidate.setAnimationStyle(xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.valueOf(animation));
            java.util.List<String> errors = LotteryPoolValidator.validate(candidate);
            if (!errors.isEmpty()) {
                results.runningResult(tr(errors.contains("invalid_dates") ? "lottery_date_invalid"
                        : errors.contains("invalid_draw_modes") ? "lottery_draw_sizes_invalid" : "input_format_invalid"));
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

    private static InputFormScreen.Widget itemField(String name, String title, String initial) {
        java.util.concurrent.atomic.AtomicReference<String> selection = new java.util.concurrent.atomic.AtomicReference<>(initial);
        return new InputFormScreen.Widget().name(name).title(text(title))
                .type(InputFormScreen.WidgetType.PICKER).pickerReadOnly(true)
                .maxLength(LotteryPoolValidator.MAX_ARTWORK_ID).defaultValue(initial)
                .validator(result -> result.value().trim().isEmpty() || !LotteryPoolValidator.isArtworkItem(result.value().trim())
                        ? tr("input_format_invalid") : "")
                .picker((screen, selected) -> ItemRewardSelectionFlow.create(screen, itemStack(selection.get()), stack -> {
                    if (stack.isEmpty()) return;
                    String value = xin.vanilla.banira.common.util.ItemUtils.getItemRegistry(stack.getItem()).toString();
                    selection.set(value);
                    selected.accept(value);
                }, false));
    }

    private static net.minecraft.world.item.ItemStack itemStack(String id) {
        if (id.trim().isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        return xin.vanilla.banira.common.util.ItemUtils.deserializeItemStack(id);
    }

    private static String itemName(String id) {
        return id;
    }

    private static InputFormScreen.Widget artworkField(String name, String title, ArtworkDraft artwork, boolean thumbnail) {
        return new InputFormScreen.Widget().name(name).title(text(title))
                .type(InputFormScreen.WidgetType.PICKER).pickerReadOnly(true)
                .defaultValue(artwork.summary(thumbnail))
                .picker((screen, selected) -> artworkPicker(screen, title, artwork, thumbnail, selected));
    }

    private static Screen artworkPicker(Screen parent, String title, ArtworkDraft artwork,
                                        boolean thumbnail, Consumer<String> selected) {
        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen(parent).setHeaderTitle(text(title));
        java.util.List<DropdownOption> modes = new java.util.ArrayList<DropdownOption>();
        modes.add(option(thumbnail ? "auto" : "none", thumbnail ? "lottery_artwork_auto" : "lottery_artwork_none"));
        modes.add(option("png", "lottery_artwork_file"));
        if (!thumbnail) {
            modes.add(option("item", "lottery_artwork_item"));
            modes.add(option("texture", "lottery_artwork_texture"));
        }
        String storedPng = thumbnail ? artwork.thumbnail : artwork.poster;
        args.addWidget(dropdown("mode", "lottery_artwork_source", artwork.source(thumbnail), modes.toArray(new DropdownOption[0])));
        if (!storedPng.isEmpty()) {
            args.addWidget(new InputFormScreen.Widget().name("savedPng").title(text("lottery_png_current"))
                    .defaultValue(pngDimensions(storedPng)).disabled(true)
                    .visibleWhen(result -> "png".equals(result.value("mode"))));
        }
        args.addWidget(new InputFormScreen.Widget().name("png")
                .title(text(storedPng.isEmpty() ? "lottery_artwork_file" : "lottery_png_replace"))
                .tooltip(text("lottery_png_hint")).type(InputFormScreen.WidgetType.FILE)
                .fileFilter("png").allowEmpty(!storedPng.isEmpty()).maxLength(2048)
                .visibleWhen(result -> "png".equals(result.value("mode"))));
        if (!thumbnail) {
            args.addWidget(itemField("item", "lottery_artwork_item", artwork.item)
                    .visibleWhen(result -> "item".equals(result.value("mode"))));
            args.addWidget(new InputFormScreen.Widget().name("texture").title(text("lottery_artwork_texture"))
                    .tooltip(text("lottery_artwork_texture_hint"))
                    .maxLength(LotteryPoolValidator.MAX_ARTWORK_ID).defaultValue(artwork.texture)
                    .validator(result -> !result.value().trim().isEmpty() && LotteryPoolValidator.isArtworkTexture(result.value().trim())
                            ? "" : tr("input_format_invalid"))
                    .visibleWhen(result -> "texture".equals(result.value("mode"))));
        }
        args.setCallback(results -> {
            try {
                switch (results.value("mode")) {
                    case "png": {
                        String path = results.value("png");
                        String png = path.trim().isEmpty() && !storedPng.isEmpty() ? storedPng : readImage(path);
                        if (thumbnail) artwork.thumbnail = png;
                        else artwork.main("", "", png);
                        break;
                    }
                    case "item": {
                        if (thumbnail) throw new IllegalArgumentException("Invalid thumbnail source");
                        String item = results.value("item").trim();
                        if (item.trim().isEmpty() || !LotteryPoolValidator.isArtworkItem(item)) {
                            results.runningResult(tr("input_format_invalid"));
                            return;
                        }
                        artwork.main(item, "", "");
                        break;
                    }
                    case "texture": {
                        if (thumbnail) throw new IllegalArgumentException("Invalid thumbnail source");
                        String texture = results.value("texture").trim();
                        if (texture.trim().isEmpty() || !LotteryPoolValidator.isArtworkTexture(texture)) {
                            results.runningResult(tr("input_format_invalid"));
                            return;
                        }
                        artwork.main("", texture, "");
                        break;
                    }
                    case "auto": {
                        if (!thumbnail) throw new IllegalArgumentException("Invalid poster source");
                        artwork.thumbnail = "";
                        break;
                    }
                    case "none": {
                        if (thumbnail) throw new IllegalArgumentException("Invalid thumbnail source");
                        artwork.main("", "", "");
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unknown artwork source");
                }
                selected.accept(artwork.summary(thumbnail));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                results.runningResult(tr("lottery_png_invalid"));
            }
        });
        return new InputFormScreen(args);
    }

    private static String pngDimensions(String png) {
        try {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(java.util.Base64.getDecoder().decode(png));
            if (header.remaining() >= 24) return "PNG · " + header.getInt(16) + " × " + header.getInt(20);
        } catch (IllegalArgumentException ignored) {
        }
        return tr("lottery_png_invalid");
    }

    private static String readImage(String path) throws java.io.IOException {
        if (path.trim().isEmpty()) throw new IllegalArgumentException("Missing PNG path");
        try (java.io.InputStream input = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(path))) {
            byte[] buffer = new byte[xin.vanilla.sakura.data.lottery.LotteryPng.MAX_BYTES + 1];
            int length = 0;
            while (length < buffer.length) {
                int read = input.read(buffer, length, buffer.length - length);
                if (read < 0) break;
                length += read;
            }
            byte[] bytes = java.util.Arrays.copyOf(buffer, length);
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
            switch (source(thumbnail)) {
                case "png":
                    return pngDimensions(thumbnail ? this.thumbnail : poster);
                case "item":
                    return tr("lottery_artwork_item") + " · " + itemName(item);
                case "texture":
                    return tr("lottery_artwork_texture") + " · " + texture;
                case "auto":
                    return tr("lottery_artwork_auto");
                default:
                    return tr("lottery_artwork_none");
            }
        }

        private String source(boolean thumbnail) {
            if (thumbnail) return this.thumbnail.isEmpty() ? "auto" : "png";
            if (!poster.isEmpty()) return "png";
            if (!texture.isEmpty()) return "texture";
            return item.isEmpty() ? "none" : "item";
        }
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
                .picker((screen, callback) -> ItemRewardSelectionFlow.create(screen, selectedItem.get().isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY
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
                else
                    expression = LotteryVoucherExpressions.build(kind, results.value("score".equals(kind) ? "objective" : "item"),
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
                case "free":
                    return tr("lottery_free");
                case "item":
                    return cost.specification + amount;
                case "score":
                    return tr("lottery_voucher_score") + ": " + cost.specification + amount;
                case "xpPoints":
                    return tr("lottery_voucher_points") + amount;
                case "xpLevels":
                    return tr("lottery_voucher_levels") + amount;
                default:
                    return tr("lottery_voucher_advanced");
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
        return new DropdownOption(value, label, net.minecraft.world.item.ItemStack.EMPTY, null,
                SakuraComponent.get().literal(description.endsWith("." + key + "_tooltip") ? label : description));
    }

    private static Text text(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
