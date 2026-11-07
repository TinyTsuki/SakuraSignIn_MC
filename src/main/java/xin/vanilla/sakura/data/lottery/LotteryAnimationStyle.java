package xin.vanilla.sakura.data.lottery;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 客户端抽奖表现不参与服务端结果计算。
 */
public enum LotteryAnimationStyle implements IEnumDescribable {
    STRIP,
    CARDS,
    ROULETTE,
    INSTANT,
    ENVELOPE,
    FAN,
    BOUNCE,
    CHEST,
    FLIP;

    public boolean illustrated() {
        return ordinal() >= ENVELOPE.ordinal();
    }

    public static java.util.List<LotteryAnimationStyle> editorChoices(LotteryAnimationStyle current) {
        var choices = new java.util.ArrayList<>(java.util.List.of(ENVELOPE, FAN, BOUNCE, CHEST, FLIP));
        if (current != null && !current.illustrated()) choices.add(current);
        return java.util.List.copyOf(choices);
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
