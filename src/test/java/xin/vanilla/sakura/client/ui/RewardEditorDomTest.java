package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.internal.client.ui.UiDomAccess;
import xin.vanilla.banira.internal.client.ui.UiDomView;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardEditorDomTest {
    @org.junit.Rule
    public final xin.vanilla.sakura.test.NativeClientRule nativeClient = new xin.vanilla.sakura.test.NativeClientRule();

    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    private static final class Node {
        String text = "", value = "";
        UiGameElement element;
        final Map<String, String> attributes = new HashMap<>();
        final UiDomAccess.Node api = (UiDomAccess.Node) Proxy.newProxyInstance(UiDomAccess.Node.class.getClassLoader(),
                new Class<?>[]{UiDomAccess.Node.class}, (self, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return self == args[0];
                        case "hashCode":
                            return System.identityHashCode(self);
                        case "text":
                            text = (String) args[0];
                            return null;
                        case "gameElement":
                            element = (UiGameElement) args[0];
                            return null;
                        case "value":
                            if (args == null) return value;
                            value = (String) args[0];
                            return null;
                        case "attribute":
                            attributes.put((String) args[0], (String) args[1]);
                            return null;
                        case "removeAttribute":
                            attributes.remove(args[0]);
                            return null;
                        case "bounds":
                            return new ScreenCoordinate(0, 0, 400, 300);
                        case "clientHeight":
                            return 84d;
                        case "scrollHeight":
                            return Double.POSITIVE_INFINITY;
                        case "scrollTop":
                            return method.getReturnType() == double.class ? 0d : null;
                        case "focused":
                        case "checked":
                            return false;
                        case "selectionStart":
                        case "selectionEnd":
                            return 0;
                        case "on":
                            return (AutoCloseable) () -> {
                            };
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void actualDomAdapterRetainsProbabilityTextAndNoResetGlyph() {
        Map<String, Node> ids = new HashMap<>();
        for (String id : List.of("fields", "content", "workspace", "body")) ids.put(id, new Node());
        List<Node> created = new ArrayList<>();
        UiDomAccess access = (UiDomAccess) Proxy.newProxyInstance(UiDomAccess.class.getClassLoader(),
                new Class<?>[]{UiDomAccess.class}, (self, method, args) -> switch (method.getName()) {
                    case "find" -> ids.containsKey(args[0]) ? ids.get(args[0]).api : null;
                    case "create" -> {
                        Node node = new Node();
                        created.add(node);
                        yield node.api;
                    }
                    case "overlayRoot" -> ids.get("body").api;
                    case "current" -> true;
                    default -> null;
                });
        var session = new RewardEditorUiSession(new RewardEditorUiParityTest.Port(2), () -> true);
        var view = new UiDomView(new Object(), access);
        session.bind(view);
        view.afterLayout();
        assertTrue("Inline probabilities must survive the real UiDomView transformation",
                created.stream().anyMatch(node -> node.text.equals("%") && node.element == null));
        assertFalse(created.stream().anyMatch(node -> node.element != null && node.element.kind() == UiGameElement.Kind.PRESET
                && node.element.preset() == xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle.RESET));
        session.close(UiCloseReason.DISCONNECT);
        view.close();
    }
}
