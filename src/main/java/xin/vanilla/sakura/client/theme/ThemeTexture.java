package xin.vanilla.sakura.client.theme;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.util.AbstractGuiUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A validated complete PNG and its alpha hit mask.
 */
public final class ThemeTexture {
    private final ResourceLocation location;
    private final int width, height;
    private final byte[] png, alpha;
    private final boolean isolated;

    ThemeTexture(ResourceLocation location, BufferedImage image, byte[] png) {
        this.location = location;
        width = image.getWidth();
        height = image.getHeight();
        this.png = png.clone();
        isolated = false;
        alpha = new byte[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) alpha[y * width + x] = (byte) (image.getRGB(x, y) >>> 24);
    }

    private ThemeTexture(ThemeTexture source, ResourceLocation location) {
        this.location = location;
        width = source.width;
        height = source.height;
        png = source.png;
        alpha = source.alpha;
        isolated = true;
    }

    ThemeTexture isolated() {
        try {
            String sha = com.google.common.io.BaseEncoding.base16().lowerCase().encode(MessageDigest.getInstance("SHA-256").digest(png));
            return new ThemeTexture(this, new ResourceLocation("sakura_sign_in", "runtime/themes/" + sha + ".png"));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public ResourceLocation location() {
        return location;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    boolean requiresUpload() {
        return isolated;
    }

    InputStream openPng() {
        return new ByteArrayInputStream(png);
    }

    public boolean opaqueAt(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && alpha[y * width + x] != 0;
    }

    public Texture texture() {
        return Texture.of(location, width, height);
    }

    public void draw(MatrixStack stack, int x, int y, int w, int h) {
        AbstractGuiUtils.blit(stack, location, x, y, w, h, 0, 0, width, height, width, height);
    }
}
