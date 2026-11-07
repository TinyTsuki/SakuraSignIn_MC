package xin.vanilla.sakura.client.gui.lottery;

import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.api.cost.CostRequirement;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.banira.common.util.ItemUtils;
import xin.vanilla.sakura.SakuraComponent;

public record LotteryFeePresentation(String text, String tooltip, UiGameElement icon) {
    public static LotteryFeePresentation of(String summary) {
        if (summary == null) return new LotteryFeePresentation("\u2014", "", null);
        if (summary.isEmpty()) return new LotteryFeePresentation(tr("lottery_free"), tr("lottery_free"), null);
        try {
            var cost = BaniraCosts.parse(summary);
            if (cost.kind() == CostRequirement.Kind.LEAF) {
                var leaf = cost.resource();
                if (leaf.resourceId().equals("banira:item")) {
                    var item = ItemUtils.deserializeItemStack(leaf.specification());
                    if (!item.isEmpty()) return new LotteryFeePresentation("\u00d7" + leaf.amount(),
                            item.getHoverName().getString() + " \u00d7" + leaf.amount(),
                            UiGameElement.option(new DropdownOption("", "", item, null, null)));
                }
                String key = switch (leaf.resourceId()) {
                    case "banira:xp_point" -> "lottery_voucher_points";
                    case "banira:xp_level" -> "lottery_voucher_levels";
                    case "banira:score" -> "lottery_voucher_score";
                    default -> "";
                };
                if (!key.isEmpty()) {
                    String text = tr(key) + (leaf.resourceId().equals("banira:score") ? " · " + leaf.specification() : "") + " \u00d7" + leaf.amount();
                    return new LotteryFeePresentation(text, text, null);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Preserve the server's complete summary when it is not a single recognized resource.
        }
        return new LotteryFeePresentation(summary, summary, null);
    }

    private static String tr(String key) {
        return SakuraComponent.get().translateClient("word", key);
    }
}
