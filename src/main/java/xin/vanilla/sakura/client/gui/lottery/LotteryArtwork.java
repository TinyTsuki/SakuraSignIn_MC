package xin.vanilla.sakura.client.gui.lottery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.sakura.data.lottery.LotteryPool;

public record LotteryArtwork(Identifier texture, ItemStack item, int imageWidth, int imageHeight) {
    private static final int WIDTH = 512, HEIGHT = 288;

    public static LotteryArtwork resolve(LotteryPool pool) {
        return resolve(pool, false);
    }

    public static LotteryArtwork thumbnail(LotteryPool pool) {
        return resolve(pool, true);
    }

    private static LotteryArtwork resolve(LotteryPool pool, boolean thumbnail) {
        var uploaded = pool == null ? null : LotteryArtworkCache.get(thumbnail ? pool.getThumbnailPng() : pool.getPosterPng());
        if (uploaded == null && thumbnail && pool != null) uploaded = LotteryArtworkCache.get(pool.getPosterPng());
        if (uploaded != null)
            return new LotteryArtwork(uploaded.id(), ItemStack.EMPTY, uploaded.width(), uploaded.height());
        Identifier texture = pool == null || pool.getArtworkTexture().isEmpty() ? null : Identifier.tryParse(pool.getArtworkTexture());
        Minecraft client = Minecraft.getInstance();
        if (texture != null && (client == null || client.getResourceManager().getResource(texture).isEmpty()))
            texture = null;
        ItemStack item = pool == null || pool.getArtworkItem().isEmpty() ? ItemStack.EMPTY
                : xin.vanilla.banira.common.util.ItemUtils.deserializeItemStack(pool.getArtworkItem());
        return new LotteryArtwork(texture, item.isEmpty() ? new ItemStack(Items.CHERRY_SAPLING) : item, WIDTH, HEIGHT);
    }

    public static boolean hasMain(LotteryPool pool) {
        return !pool.getPosterPng().isEmpty() || !pool.getArtworkItem().isEmpty() || !pool.getArtworkTexture().isEmpty();
    }

    public static DropdownOption option(LotteryPool pool, xin.vanilla.banira.common.data.Component tooltip) {
        if (!hasMain(pool) && pool.getThumbnailPng().isEmpty())
            return new DropdownOption(pool.getId(), pool.getDisplayName(), ItemStack.EMPTY, null, tooltip);
        LotteryArtwork artwork = thumbnail(pool);
        Texture[] textures = artwork.texture == null ? null : new Texture[]{Texture.of(artwork.texture, artwork.imageWidth, artwork.imageHeight)};
        return new DropdownOption(pool.getId(), pool.getDisplayName(), artwork.item, textures, tooltip);
    }

    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int itemSize) {
        if (texture != null) {
            int drawWidth = Math.min(width, height * imageWidth / imageHeight);
            int drawHeight = drawWidth * imageHeight / imageWidth;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                    0f, 0f, drawWidth, drawHeight, imageWidth, imageHeight, imageWidth, imageHeight, 0xFFFFFFFF);
            return;
        }
        int size = Math.min(itemSize, Math.min(width, height));
        if (size < 1) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x + (width - size) / 2f, y + (height - size) / 2f);
            pose.scale(size / 16.0F, size / 16.0F);
            graphics.item(item, 0, 0);
        } finally {
            pose.popMatrix();
        }
    }
}
