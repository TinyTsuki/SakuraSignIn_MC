package xin.vanilla.sakura.data.personaldate;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

public enum PersonalDateCalendarPolicy implements IEnumDescribable {
    SOLAR_ONLY,
    LUNAR_ONLY,
    PLAYER_CHOICE;

    public boolean accepts(PersonalDateCalendar calendar) {
        return this == PLAYER_CHOICE
                || (this == SOLAR_ONLY && calendar == PersonalDateCalendar.SOLAR)
                || (this == LUNAR_ONLY && calendar == PersonalDateCalendar.LUNAR);
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
