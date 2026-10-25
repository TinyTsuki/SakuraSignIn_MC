package xin.vanilla.sakura.screen.coordinate;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.io.Serializable;

/** Theme layout in logical pixels; image regions are not part of layout. */
@Getter
@Setter
@Accessors(chain = true)
public class TextureCoordinate implements Serializable {
    private static final long serialVersionUID = 2L;
    private boolean special;
    private int width = 500;
    private int height = 600;
    private int tooltipWidth = 300;
    private int tooltipHeight = 96;
    private int weekStart = 7;
    private int textColorDefault = 0xFFDBDBDB;
    private int textColorCurrent = 0xFFFFFFFF;
    private int textColorCanRepair = 0xFFFFFFBB;
    private int textColorToday = 0xFF474747;
    private int textColorDate = 0xFF000000;
    @NonNull private Coordinate yearCoordinate = new Coordinate(128, 60, 92, 16);
    @NonNull private Coordinate monthCoordinate = new Coordinate(320, 60, 62, 16);
    @NonNull private Coordinate cellCoordinate = new Coordinate(73, 134, 36, 36);
    private int cellHMargin = 18;
    private int cellVMargin = 26;
    private int dateOffset = 36;
    @NonNull private Coordinate leftArrowCoordinate = new Coordinate(320, 60, 62, 16);
    @NonNull private Coordinate rightArrowCoordinate = new Coordinate(320, 60, 62, 16);
    @NonNull private Coordinate upArrowCoordinate = new Coordinate(128, 60, 92, 16);
    @NonNull private Coordinate downArrowCoordinate = new Coordinate(128, 60, 92, 16);
    @NonNull private Coordinate signInInfoCoordinate = new Coordinate(400, 0, 100, 100);
    @NonNull private Coordinate themeCoordinate = new Coordinate(300, 552, 40, 48);
    private int themeHMargin;
    private int tooltipCellHMargin = 5;
    @NonNull private Coordinate tooltipCellCoordinate = new Coordinate(15, 10, 42, 42);
    @NonNull private Coordinate tooltipDateCoordinate = new Coordinate(110, 64, 50, 19);
    @NonNull private Coordinate tooltipScrollCoordinate = new Coordinate(10, 93, 280, 1);
    public @NonNull Coordinate getYearCoordinate() { return yearCoordinate = yearCoordinate == null ? new Coordinate() : yearCoordinate; }
    public @NonNull Coordinate getMonthCoordinate() { return monthCoordinate = monthCoordinate == null ? new Coordinate() : monthCoordinate; }
    public @NonNull Coordinate getCellCoordinate() { return cellCoordinate = cellCoordinate == null ? new Coordinate() : cellCoordinate; }
    public @NonNull Coordinate getLeftArrowCoordinate() { return leftArrowCoordinate = leftArrowCoordinate == null ? new Coordinate() : leftArrowCoordinate; }
    public @NonNull Coordinate getRightArrowCoordinate() { return rightArrowCoordinate = rightArrowCoordinate == null ? new Coordinate() : rightArrowCoordinate; }
    public @NonNull Coordinate getUpArrowCoordinate() { return upArrowCoordinate = upArrowCoordinate == null ? new Coordinate() : upArrowCoordinate; }
    public @NonNull Coordinate getDownArrowCoordinate() { return downArrowCoordinate = downArrowCoordinate == null ? new Coordinate() : downArrowCoordinate; }
    public @NonNull Coordinate getSignInInfoCoordinate() { return signInInfoCoordinate = signInInfoCoordinate == null ? new Coordinate() : signInInfoCoordinate; }
    public @NonNull Coordinate getThemeCoordinate() { return themeCoordinate = themeCoordinate == null ? new Coordinate() : themeCoordinate; }
    public @NonNull Coordinate getTooltipCellCoordinate() { return tooltipCellCoordinate = tooltipCellCoordinate == null ? new Coordinate() : tooltipCellCoordinate; }
    public @NonNull Coordinate getTooltipDateCoordinate() { return tooltipDateCoordinate = tooltipDateCoordinate == null ? new Coordinate() : tooltipDateCoordinate; }
    public @NonNull Coordinate getTooltipScrollCoordinate() { return tooltipScrollCoordinate = tooltipScrollCoordinate == null ? new Coordinate() : tooltipScrollCoordinate; }
    public static @NonNull TextureCoordinate getDefault() { return new TextureCoordinate(); }
}
