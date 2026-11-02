package xin.vanilla.sakura.client.theme;

import net.minecraft.util.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns only synthetic packaged fallback textures; resource-pack textures remain Minecraft-owned.
 */
final class ThemeTextureBindings {
    private Set<ResourceLocation> active = com.google.common.collect.ImmutableSet.of();

    static <I extends AutoCloseable, T> T transfer(I image, Function<I, T> construct) {
        try {
            return Objects.requireNonNull(construct.apply(image));
        } catch (RuntimeException | Error failure) {
            try {
                image.close();
            } catch (Exception suppressed) {
                failure.addSuppressed(suppressed);
            }
            throw failure;
        }
    }

    void replace(Collection<ThemeTexture> textures, Consumer<ThemeTexture> upload, Consumer<ResourceLocation> release) {
        Map<ResourceLocation, ThemeTexture> next = new LinkedHashMap<>();
        for (lombok.var texture : textures) if (texture.requiresUpload()) next.put(texture.location(), texture);
        List<ResourceLocation> added = new ArrayList<>();
        try {
            for (lombok.var entry : next.entrySet())
                if (!active.contains(entry.getKey())) {
                    added.add(entry.getKey());
                    upload.accept(entry.getValue());
                }
        } catch (RuntimeException | Error failure) {
            for (lombok.var id : added)
                try {
                    release.accept(id);
                } catch (RuntimeException | Error suppressed) {
                    failure.addSuppressed(suppressed);
                }
            throw failure;
        }
        for (lombok.var id : active) if (!next.containsKey(id)) release.accept(id);
        active = com.google.common.collect.ImmutableSet.copyOf(next.keySet());
    }
}
