package xin.vanilla.sakura.data.personaldate;

import lombok.Value;

@Value
public class LunarDate {
    int year;
    int month;
    int day;
    boolean leapMonth;
}
