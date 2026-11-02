package xin.vanilla.sakura.client.ui;

import net.minecraft.client.gui.screen.Screen;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.sakura.client.reward.RewardEditorCoordinator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class RewardEditorUiContinuationTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    private static void init(Object screen) throws Exception {
        Method method = screen.getClass().getDeclaredMethod("initWidgets");
        method.setAccessible(true);
        method.invoke(screen);
    }

    @Test
    public void deferredReturnCannotRepeatContinuationOnResizeOrReentry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Screen transition = RewardEditorCoordinator.deferred(null, calls::incrementAndGet, () -> true);
        init(transition);
        init(transition);
        init(transition);
        assertEquals("Deferred return must advance once", 1, calls.get());
    }

    @Test
    public void submittedValueIsConsumedOnceBeforeContinuing() throws Exception {
        Class<?> type = Class.forName("xin.vanilla.sakura.client.reward.RewardEditorCoordinator$ValueTransitionScreen");
        Constructor<?> constructor = type.getDeclaredConstructor(Screen.class, Consumer.class);
        constructor.setAccessible(true);
        AtomicInteger calls = new AtomicInteger();
        Object transition = constructor.newInstance(null, (Consumer<String>) value -> {
            assertEquals("draft", value);
            calls.incrementAndGet();
        });
        Method submit = type.getDeclaredMethod("submit", Object.class);
        submit.setAccessible(true);
        submit.invoke(transition, "draft");
        init(transition);
        init(transition);
        submit.invoke(transition, "late");
        init(transition);
        assertEquals("Value return must advance once", 1, calls.get());
    }

    @Test
    public void finalRewardCallbackCanBeDeliveredOnlyOnce() throws Exception {
        Method method = RewardEditorCoordinator.class.getDeclaredMethod("connectedOnce", Consumer.class);
        method.setAccessible(true);
        AtomicInteger calls = new AtomicInteger();
        @SuppressWarnings("unchecked") Consumer<String> callback = (Consumer<String>) method.invoke(null, (Consumer<String>) ignored -> calls.incrementAndGet());
        callback.accept("first");
        callback.accept("duplicate");
        assertEquals(1, calls.get());
    }
}
