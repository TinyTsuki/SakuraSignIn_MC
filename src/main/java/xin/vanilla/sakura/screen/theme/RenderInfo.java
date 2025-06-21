package xin.vanilla.sakura.screen.theme;

import lombok.Data;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumRotationCenter;
import xin.vanilla.sakura.enums.EnumThemeTextureFillType;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.Component;

import java.io.Serializable;

/**
 * 渲染信息
 */
@Data
@Accessors(chain = true)
public class RenderInfo implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    /**
     * 坐标信息
     */
    private Coordinate coordinate = new Coordinate();
    /**
     * 背景色
     */
    private int bgColor;
    /**
     * 前景色
     */
    private int fgColor;
    /**
     * 深度(渲染顺序)
     */
    private int depth;
    /**
     * 旋转角度
     */
    private double rotationAngle;
    /**
     * 旋转中心
     */
    private EnumRotationCenter rotationCenter = EnumRotationCenter.CENTER;
    /**
     * 水平翻转
     */
    private boolean flipHorizontal;
    /**
     * 垂直翻转
     */
    private boolean flipVertical;
    /**
     * 纹理大小相对于绘制大小的缩放比例</br>
     * 仅对 EnumThemeTextureFillType.TILE 与 EnumThemeTextureFillType.CENTER 有效
     */
    private double scale = 1.0;
    /**
     * 透明度
     */
    private double alpha = 0xFF;
    /**
     * 纹理渲染模式
     */
    private EnumThemeTextureFillType fillType = EnumThemeTextureFillType.STRETCH;
    /**
     * 渲染内容
     */
    private Component text;
    /**
     * 悬浮提示
     */
    private Component tooltip;

    @Override
    public RenderInfo clone() {
        try {
            RenderInfo clone = (RenderInfo) super.clone();
            if (this.coordinate != null)
                clone.coordinate = this.coordinate.clone();
            if (this.text != null)
                clone.text = this.text.clone();
            if (this.tooltip != null)
                clone.tooltip = this.tooltip.clone();
            return clone;
        } catch (Exception e) {
            return new RenderInfo();
        }
    }

    public boolean hasUVInfo() {
        return this.coordinate.getU0() != 0 || this.coordinate.getV0() != 0 || this.coordinate.getUWidth() != 0 || this.coordinate.getVHeight() != 0;
    }

    public boolean hasTextInfo() {
        return this.text != null;
    }

    public boolean hasTooltipInfo() {
        return this.tooltip != null;
    }
}
