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

/** 奖池规则使用明确选项配置，避免玩家猜测策略枚举与数值含义。 */
public final class LotteryPoolForm {
    private LotteryPoolForm() {
    }

    public static Screen create(Screen parent, LotteryPool existing,
                                Consumer<LotteryPool> submit) {
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
                    if (existing != null && !existing.getId().equals(id)) {
                        return tr("lottery_pool_id_locked");
                    }
                    boolean duplicate = RewardConfigManager.getRewardConfig().getLotteryPools()
                            .stream().anyMatch(pool -> pool != existing && id.equals(pool.getId()));
                    return duplicate ? tr("lottery_pool_duplicate") : "";
                }));
        args.addWidget(new InputFormScreen.Widget().name("name")
                .title(text("lottery_pool_name")).hint(text("lottery_pool_name_hint"))
                .tooltip(text("lottery_pool_name_hint"))
                .maxLength(96)
                .regex(".{1,96}")
                .defaultValue(existing == null ? "" : existing.getDisplayName()));
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
            if (LotteryPoolValidator.validate(candidate).isEmpty()) {
                submit.accept(candidate);
            }
        });
        return new InputFormScreen(args);
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
