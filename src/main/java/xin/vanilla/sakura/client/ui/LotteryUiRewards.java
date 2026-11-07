package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.api.client.ui.UiRowLayout;
import xin.vanilla.banira.api.client.ui.UiView;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.reward.Reward;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;

final class LotteryUiRewards {
    private LotteryUiRewards() {
    }

    static void bind(UiView view, List<Reward> rewards, String key, BooleanSupplier live,
                     Function<Reward, String> name, boolean items, boolean probabilities,
                     IntFunction<UiGameElement> icons) {
        BigDecimal total = rewards.stream().map(Reward::getProbability).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        view.rows("fields", new UiRowLayout() {
            public int rowHeight() {
                return 24;
            }

            public String rowClass(int row) {
                return "reward-row";
            }

            public int size() {
                return rewards.size();
            }

            public String key(int row) {
                return key + ":" + row;
            }

            public void bind(UiView cell, int row) {
                if (!live.getAsBoolean()) return;
                Reward reward = rewards.get(row);
                String label = items ? name.apply(reward) : "?";
                String detail = "", tooltipDetail = "";
                if (probabilities) {
                    BigDecimal weight = reward.getProbability() == null ? BigDecimal.ZERO : reward.getProbability();
                    BigDecimal percent = total.signum() <= 0 ? BigDecimal.ZERO : weight.multiply(BigDecimal.valueOf(100))
                            .divide(total, 2, RoundingMode.HALF_UP).stripTrailingZeros();
                    detail = percent.toPlainString() + "%";
                    tooltipDetail = SakuraComponent.get().translateClient("format", "lottery_probability_display_s", detail);
                }
                cell.gameElement("icon", items ? icons.apply(row) : null);
                cell.text("icon", SakuraComponent.get().literal(items ? "" : "?"));
                cell.text("label", SakuraComponent.get().literal(label));
                cell.text("detail", SakuraComponent.get().literal(detail));
                var tooltip = SakuraComponent.get().literal(label + (tooltipDetail.isEmpty() ? "" : "\n" + tooltipDetail));
                cell.tooltip("label", tooltip);
                cell.tooltip("icon", tooltip);
                cell.tooltip("detail", tooltip);
            }
        }, 40);
    }
}
