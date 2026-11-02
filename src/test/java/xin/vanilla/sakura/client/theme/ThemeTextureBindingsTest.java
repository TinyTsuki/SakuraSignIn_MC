package xin.vanilla.sakura.client.theme;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ThemeTextureBindingsTest {
    @Test
    public void constructionFailureClosesTheImageBeforeOwnershipTransfer() {
        int[] closes = {0};
        AutoCloseable image = () -> closes[0]++;
        assertThrows(IllegalStateException.class, () -> ThemeTextureBindings.transfer(image, value -> {
            throw new IllegalStateException("constructor upload failed");
        }));
        assertEquals(1, closes[0]);
        assertSame(image, ThemeTextureBindings.transfer(image, value -> value));
        assertEquals("Successful construction transfers ownership without closing", 1, closes[0]);
        assertThrows(AssertionError.class, () -> ThemeTextureBindings.transfer(image, value -> {
            throw new AssertionError("GPU error");
        }));
        assertEquals(2, closes[0]);
    }

    @Test
    public void deduplicatesReusesAndReleasesOnlyOwnedTextures() {
        lombok.var bindings = new ThemeTextureBindings();
        lombok.var uploaded = new ArrayList<ResourceLocation>();
        lombok.var released = new ArrayList<ResourceLocation>();
        lombok.var theme = BuiltInThemeCatalog.load("sakura");
        lombok.var first = theme.texture("background.calendar").isolated();
        lombok.var second = theme.texture("icon.info").isolated();
        bindings.replace(com.google.common.collect.ImmutableList.of(first, first, second), t -> uploaded.add(t.location()), released::add);
        assertEquals(2, uploaded.size());
        assertTrue(released.isEmpty());
        bindings.replace(com.google.common.collect.ImmutableList.of(first, second), t -> uploaded.add(t.location()), released::add);
        assertEquals(2, uploaded.size());
        assertTrue(released.isEmpty());
        bindings.replace(com.google.common.collect.ImmutableList.of(theme.texture("background.calendar")), t -> uploaded.add(t.location()), released::add);
        assertEquals(com.google.common.collect.ImmutableSet.of(first.location(), second.location()), new HashSet<>(released));
        bindings.replace(com.google.common.collect.ImmutableList.of(), t -> uploaded.add(t.location()), released::add);
        assertEquals(2, released.size());
    }

    @Test
    public void failedUploadRollsBackNewTexturesAndKeepsPreviousOwnership() {
        lombok.var bindings = new ThemeTextureBindings();
        lombok.var released = new ArrayList<ResourceLocation>();
        lombok.var theme = BuiltInThemeCatalog.load("sakura");
        lombok.var old = theme.texture("background.calendar").isolated();
        lombok.var next = theme.texture("icon.info").isolated();
        bindings.replace(com.google.common.collect.ImmutableList.of(old), t -> {
        }, released::add);
        assertThrows(IllegalStateException.class, () -> bindings.replace(com.google.common.collect.ImmutableList.of(next), t -> {
            throw new IllegalStateException("upload failed");
        }, released::add));
        assertEquals(com.google.common.collect.ImmutableList.of(next.location()), released);
        bindings.replace(com.google.common.collect.ImmutableList.of(old), t -> fail("Previous upload must survive rollback"), released::add);
        bindings.replace(com.google.common.collect.ImmutableList.of(), t -> {
        }, released::add);
        assertEquals(com.google.common.collect.ImmutableList.of(next.location(), old.location()), released);
    }
}
