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
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                .regex(".{1,96}")
                .defaultValue(existing == null ? "" : existing.getDisplayName()));
        args.addWidget(dropdown("policy", "lottery_limit_policy",
                existing == null ? LotteryLimitPolicy.DAILY.name()
                        : existing.getLimitPolicy().name(),
                Arrays.stream(LotteryLimitPolicy.values()).map(policy -> option(
                        policy.name(), "lottery_policy_" + policy.name().toLowerCase()))
                        .toArray(DropdownOption[]::new)));
        List<String> counts = IntStream.rangeClosed(1, 30).mapToObj(String::valueOf)
                .collect(Collectors.toList());
        args.addWidget(valueDropdown("maxDraws", "lottery_max_draws",
                existing == null ? "1" : String.valueOf(existing.getMaxDraws()), counts,
                "lottery_max_draws_tooltip"));
        List<String> cooldowns = Arrays.asList("0", "30", "60", "300", "600", "1800",
                "3600", "21600", "43200", "86400", "604800");
        args.addWidget(valueDropdown("cooldown", "lottery_cooldown_seconds",
                existing == null ? "0" : String.valueOf(existing.getCooldownSeconds()),
                cooldowns, "lottery_cooldown_tooltip"));
        args.setCallback(results -> {
            LotteryPool candidate = new LotteryPool(results.value("id"), results.value("name"),
                    LotteryLimitPolicy.valueOf(results.value("policy")),
                    Integer.parseInt(results.value("maxDraws")),
                    Integer.parseInt(results.value("cooldown")),
                    existing == null ? new RewardList() : existing.getRewards());
            if (LotteryPoolValidator.validate(candidate).isEmpty()) {
                submit.accept(candidate);
            }
        });
        return new InputFormScreen(args);
    }

    private static InputFormScreen.Widget valueDropdown(String name, String titleKey,
                                                        String value, List<String> values,
                                                        String tooltipKey) {
        DropdownOption[] options = values.stream().map(option -> new DropdownOption(
                option, option, net.minecraft.item.ItemStack.EMPTY, null,
                SakuraComponent.get().transClient("word", tooltipKey)))
                .toArray(DropdownOption[]::new);
        return dropdown(name, titleKey, value, options);
    }

    private static InputFormScreen.Widget dropdown(String name, String titleKey,
                                                   String value, DropdownOption... options) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .type(InputFormScreen.WidgetType.DROPDOWN)
                .dropdownOptionEntries(Arrays.asList(options)).defaultValue(value);
    }

    private static DropdownOption option(String value, String key) {
        return new DropdownOption(value, tr(key), net.minecraft.item.ItemStack.EMPTY,
                null, SakuraComponent.get().transClient("word", key + "_tooltip"));
    }

    private static Text text(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
