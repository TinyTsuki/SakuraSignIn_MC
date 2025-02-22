package xin.vanilla.sakura.screen.coordinate;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Coordinate implements Serializable {
    private static final long serialVersionUID = 1L;

    public double x;
    public double y;
    public double width;
    public double height;

    public double u0;
    public double v0;
    public double uWidth;
    public double vHeight;

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
}
