package xin.vanilla.sakura.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import org.junit.rules.ExternalResource;

import java.lang.reflect.Field;

/**
 * Supplies the fields read by native screens without starting the client.
 */
public final class NativeClientRule extends ExternalResource {
    private Object previous;
    private Field instance;

    @Override
    protected void before() throws Exception {
        NativeBootstrap.initialize();
        Class<?> type = Class.forName("sun.misc.Unsafe");
        Field access = type.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Object unsafe = access.get(null);
        var allocate = type.getMethod("allocateInstance", Class.class);
        Minecraft client = (Minecraft) allocate.invoke(unsafe, Minecraft.class);
        Font font = (Font) allocate.invoke(unsafe, Font.class);
        Field splitter = Font.class.getDeclaredField("splitter");
        splitter.setAccessible(true);
        splitter.set(font, new StringSplitter((point, style) -> style.isBold() ? 7 : 6));
        Field clientFont = Minecraft.class.getDeclaredField("font");
        clientFont.setAccessible(true);
        clientFont.set(client, font);
        instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previous = instance.get(null);
        instance.set(null, client);
    }

    @Override
    protected void after() {
        if (instance == null) return;
        try {
            instance.set(null, previous);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Could not restore the native client fixture", exception);
        }
    }
}
