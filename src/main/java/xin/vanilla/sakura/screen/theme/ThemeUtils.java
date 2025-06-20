package xin.vanilla.sakura.screen.theme;

import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;

@OnlyIn(Dist.CLIENT)
public class ThemeUtils {

    public static Coordinate buildCoordinateTree(ThemeComponentList components, long currentId, ThemeComponent.RenderType renderType, int renderIndex) {
        ThemeComponent current = components.stream()
                .filter(component -> component.getId() == currentId)
                .findFirst()
                .orElse(null);
        if (current == null) return null;

        Coordinate result = current.getRenderList(renderType).get(renderIndex).getCoordinate().clone();
        Coordinate parent;
        if (current.getParentId() > 0) {
            parent = buildCoordinateTree(components, current.getParentId(), renderType, renderIndex);
        } else {
            parent = null;
        }
        return result.setParent(parent);
    }

    public static Coordinate getRealCoordinate(Coordinate coordinate, Screen screen, KeyEventManager keyEventManager) {
        Coordinate result = coordinate.clone();
        Coordinate parent;
        if (coordinate.hasParent()) {
            parent = getRealCoordinate(coordinate.getParent(), screen, keyEventManager);
        } else {
            parent = new Coordinate().setWidth(screen.width).setHeight(screen.height);
        }
        switch (coordinate.getWType()) {
            case ABSOLUTE: {
            }
            break;
            case RELATIVE_PERCENT: {
                result.setWidth(coordinate.getWidth() * parent.getWidth());
            }
            break;
            case RELATIVE_PIXEL: {
                result.setWidth(coordinate.getWidth() + parent.getWidth());
            }
            break;
            case FIT: {
                double wScale = parent.getWidth() / coordinate.getWidth();
                double scaleH = coordinate.getHeight() * wScale;
                if (scaleH < parent.getHeight()) {
                    result.setWidth(parent.getWidth());
                    result.setHeight(scaleH);
                }
            }
            break;
        }
        switch (coordinate.getHType()) {
            case ABSOLUTE: {
            }
            break;
            case RELATIVE_PERCENT: {
                result.setHeight(coordinate.getHeight() * parent.getHeight());
            }
            break;
            case RELATIVE_PIXEL: {
                result.setHeight(coordinate.getHeight() + parent.getHeight());
            }
            break;
            case FIT: {
                double hScale = parent.getHeight() / coordinate.getHeight();
                double scaleW = coordinate.getWidth() * hScale;
                if (scaleW < parent.getWidth()) {
                    result.setWidth(scaleW);
                    result.setHeight(parent.getHeight());
                }
            }
            break;
        }
        switch (coordinate.getXType()) {
            case ABSOLUTE: {
                result.setX(coordinate.getX() + parent.getX());
                result.setY(coordinate.getY() + parent.getY());
            }
            break;
            case RELATIVE_PERCENT: {
                result.setX(coordinate.getX() * parent.getWidth() + parent.getX());
                result.setY(coordinate.getY() * parent.getHeight() + parent.getY());
            }
            break;
            case RELATIVE_PIXEL: {
                if (coordinate.getX() < 0) {
                    result.setX(coordinate.getX() + parent.getX() + parent.getWidth());
                } else {
                    result.setX(coordinate.getX() + parent.getX());
                }
                if (coordinate.getY() < 0) {
                    result.setY(coordinate.getY() + parent.getY() + parent.getHeight());
                } else {
                    result.setY(coordinate.getY() + parent.getY());
                }
            }
            break;
            case CENTER: {
                result.setX((parent.getWidth() - coordinate.getWidth()) / 2 + parent.getX());
                result.setY((parent.getHeight() - coordinate.getHeight()) / 2 + parent.getY());
            }
            break;
            case RELATIVE_MOUSE: {
                result.setX(coordinate.getX() + keyEventManager.getMouseX());
            }
            break;
        }
        switch (coordinate.getYType()) {
            case ABSOLUTE: {
                result.setY(coordinate.getY() + parent.getY());
            }
            break;
            case RELATIVE_PERCENT: {
                result.setY(coordinate.getY() * parent.getHeight() + parent.getY());
            }
            break;
            case RELATIVE_PIXEL: {
                if (coordinate.getY() < 0) {
                    result.setY(coordinate.getY() + parent.getY() + parent.getHeight());
                } else {
                    result.setY(coordinate.getY() + parent.getY());
                }
            }
            break;
            case CENTER: {
                result.setY((parent.getHeight() - coordinate.getHeight()) / 2 + parent.getY());
            }
            break;
            case RELATIVE_MOUSE: {
                result.setY(coordinate.getY() + keyEventManager.getMouseY());
            }
            break;
        }
        return result.setParent(parent);
    }

}
