package xin.vanilla.sakura.client.gui.lottery;

import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.lottery.LotteryPng;
import xin.vanilla.sakura.data.lottery.LotteryPool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class LotteryArtworkCache {
    private static final Map<String, Artwork> UPLOADED = new HashMap<>();

    public static final class Artwork {
        private final ResourceLocation id;
        private final int width, height;

        private Artwork(ResourceLocation id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public ResourceLocation id() { return id; }
        public int width() { return width; }
        public int height() { return height; }
    }

    private LotteryArtworkCache() {
    }

    public static Artwork get(String data) {
        Minecraft client = Minecraft.getInstance();
        if (data.isEmpty() || client == null) return null;
        Artwork artwork = UPLOADED.computeIfAbsent(data, value -> upload(client, value));
        return artwork.id() == null ? null : artwork;
    }

    private static Artwork upload(Minecraft client, String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            LotteryPng.inspect(bytes);
            StringBuilder hash = new StringBuilder(64);
            for (byte part : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(Character.forDigit((part >>> 4) & 15, 16));
                hash.append(Character.forDigit(part & 15, 16));
            }
            ResourceLocation id = new ResourceLocation(SakuraSignIn.MODID, "lottery/upload/" + hash);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            Artwork artwork = new Artwork(id, image.getWidth(), image.getHeight());
            try {
                client.getTextureManager().register(id, new DynamicTexture(image));
            } catch (RuntimeException failure) {
                image.close();
                throw failure;
            }
            return artwork;
        } catch (IOException | java.security.NoSuchAlgorithmException | IllegalArgumentException failure) {
            return new Artwork(null, 0, 0);
        }
    }

    public static void retain(Collection<LotteryPool> pools) {
        HashSet<String> current = new HashSet<>();
        for (LotteryPool pool : pools) {
            current.add(pool.getThumbnailPng());
            current.add(pool.getPosterPng());
        }
        Minecraft client = Minecraft.getInstance();
        UPLOADED.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) return false;
            if (client != null && entry.getValue().id() != null)
                client.getTextureManager().release(entry.getValue().id());
            return true;
        });
    }
}
