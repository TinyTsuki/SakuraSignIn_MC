package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.api.client.ui.UiRowProvider;
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
    private static final List<String> COLUMNS = List.of("fields", "fields_middle", "fields_end");

    private LotteryUiRewards() {
    }

    static void enter(UiView view) {
        if (view != null && !view.nativeView() && view.state() != null)
            view.state().rowPosition(view.state().rowPosition() / 3);
    }

    static void leave(UiView view) {
        if (view != null && !view.nativeView() && view.state() != null)
            view.state().rowPosition(view.state().rowPosition() * 3);
    }

    static void bind(UiView view, List<Reward> rewards, String key, BooleanSupplier live,
                     Function<Reward, String> name, boolean items, boolean probabilities,
                     IntFunction<UiGameElement> icons) {
        BigDecimal total = rewards.stream().map(Reward::getProbability).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int column = 0; column < COLUMNS.size(); column++) {
            int offset = column;
            view.rows(COLUMNS.get(column), new UiRowProvider() {
                public int size() {
                    return (rewards.size() + 2 - offset) / 3;
                }

                public String key(int row) {
                    return key + ":" + (row * 3 + offset);
                }

                public void bind(UiView cell, int row) {
                    if (!live.getAsBoolean()) return;
                    int index = row * 3 + offset;
                    Reward reward = rewards.get(index);
                    String label = items ? name.apply(reward) : "?";
                    String detail = "", tooltipDetail = "";
                    if (probabilities) {
                        BigDecimal weight = reward.getProbability() == null ? BigDecimal.ZERO : reward.getProbability();
                        BigDecimal percent = total.signum() <= 0 ? BigDecimal.ZERO : weight.multiply(BigDecimal.valueOf(100))
                                .divide(total, 2, RoundingMode.HALF_UP).stripTrailingZeros();
                        detail = percent.toPlainString() + "%";
                        tooltipDetail = SakuraComponent.get().translateClient("format", "lottery_probability_display_s", detail);
                    }
                    cell.gameElement("icon", items ? icons.apply(index) : null);
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
}
