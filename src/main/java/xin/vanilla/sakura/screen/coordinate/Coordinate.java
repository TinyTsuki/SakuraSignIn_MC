package xin.vanilla.sakura.screen.coordinate;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumCoordinateType;
import xin.vanilla.sakura.enums.EnumSizeType;
import xin.vanilla.sakura.screen.theme.Theme;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Coordinate implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private Coordinate parent;

    private double x;
    private double y;
    private EnumCoordinateType xType = EnumCoordinateType.ABSOLUTE;
    private EnumCoordinateType yType = EnumCoordinateType.ABSOLUTE;

    private double width;
    private double height;
    private EnumSizeType wType = EnumSizeType.ABSOLUTE;
    private EnumSizeType hType = EnumSizeType.ABSOLUTE;

    private double u0;
    private double v0;
    private int uWidth;
    private int vHeight;

    private int uvWidth;
    private int uvHeight;

    private String textureId = "";

    public Coordinate(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Coordinate(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean hasParent() {
        return this.parent != null;
    }

    @Override
    public Coordinate clone() {
        try {
            Coordinate cloned = (Coordinate) super.clone();
            if (this.parent != null) {
                cloned.parent = this.parent.clone();
            }
            return cloned;
        } catch (Exception e) {
            return new Coordinate();
        }
    }

    public Coordinate readUV(Theme theme) {
        Coordinate coordinate = theme.getTexureMap().getOrDefault(this.textureId, this);
        this.u0 = coordinate.getU0();
        this.v0 = coordinate.getV0();
        this.uWidth = coordinate.getUWidth();
        this.vHeight = coordinate.getVHeight();
        this.uvWidth = coordinate.getUvWidth();
        this.uvHeight = coordinate.getUvHeight();
        return this;
    }

}
