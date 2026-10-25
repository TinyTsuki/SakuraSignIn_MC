package xin.vanilla.sakura.client.theme;

import org.junit.Test;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static org.junit.Assert.*;

public class ThemeTextureBindingsTest {
    @Test public void constructionFailureClosesTheImageBeforeOwnershipTransfer() {
        int[] closes = {0}; AutoCloseable image = () -> closes[0]++;
        assertThrows(IllegalStateException.class, () -> ThemeTextureBindings.transfer(image, value -> {throw new IllegalStateException("constructor upload failed");}));
        assertEquals(1, closes[0]);
        assertSame(image, ThemeTextureBindings.transfer(image, value -> value));
        assertEquals("Successful construction transfers ownership without closing", 1, closes[0]);
        assertThrows(AssertionError.class, () -> ThemeTextureBindings.transfer(image, value -> {throw new AssertionError("GPU error");}));
        assertEquals(2, closes[0]);
    }
    @Test public void deduplicatesReusesAndReleasesOnlyOwnedTextures() {
        var bindings = new ThemeTextureBindings(); var uploaded = new ArrayList<ResourceLocation>(); var released = new ArrayList<ResourceLocation>();
        var theme = BuiltInThemeCatalog.load("sakura");
        var first = theme.texture("background.calendar").isolated(); var second = theme.texture("icon.info").isolated();
        bindings.replace(List.of(first, first, second), t -> uploaded.add(t.location()), released::add);
        assertEquals(2, uploaded.size()); assertTrue(released.isEmpty());
        bindings.replace(List.of(first, second), t -> uploaded.add(t.location()), released::add);
        assertEquals(2, uploaded.size()); assertTrue(released.isEmpty());
        bindings.replace(List.of(theme.texture("background.calendar")), t -> uploaded.add(t.location()), released::add);
        assertEquals(Set.of(first.location(), second.location()), new HashSet<>(released));
        bindings.replace(List.of(), t -> uploaded.add(t.location()), released::add); assertEquals(2, released.size());
    }
    @Test public void failedUploadRollsBackNewTexturesAndKeepsPreviousOwnership() {
        var bindings = new ThemeTextureBindings(); var released = new ArrayList<ResourceLocation>();
        var theme = BuiltInThemeCatalog.load("sakura");
        var old = theme.texture("background.calendar").isolated(); var next = theme.texture("icon.info").isolated();
        bindings.replace(List.of(old), t -> {}, released::add);
        assertThrows(IllegalStateException.class, () -> bindings.replace(List.of(next), t -> {throw new IllegalStateException("upload failed");}, released::add));
        assertEquals(List.of(next.location()), released);
        bindings.replace(List.of(old), t -> fail("Previous upload must survive rollback"), released::add);
        bindings.replace(List.of(), t -> {}, released::add); assertEquals(List.of(next.location(), old.location()), released);
    }
}
