package xin.vanilla.sakura.client.gui.lottery;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.sakura.data.lottery.LotteryPool;

public record LotteryArtwork(ResourceLocation texture, ItemStack item, int imageWidth, int imageHeight) {
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
        ResourceLocation texture = pool == null || pool.getArtworkTexture().isEmpty() ? null : ResourceLocation.tryParse(pool.getArtworkTexture());
        Minecraft client = Minecraft.getInstance();
        if (texture != null && (client == null || client.getResourceManager().getResource(texture).isEmpty()))
            texture = null;
        ItemStack item = pool == null || pool.getArtworkItem().isEmpty() ? ItemStack.EMPTY
                : xin.vanilla.banira.common.util.ItemUtils.deserializeItemStack(pool.getArtworkItem());
        return new LotteryArtwork(texture, item.isEmpty() ? new ItemStack(Items.OAK_SAPLING) : item, WIDTH, HEIGHT);
    }

    public xin.vanilla.banira.api.client.ui.UiGameElement element() {
        if (texture != null)
            return xin.vanilla.banira.api.client.ui.UiGameElement.artwork(new DropdownOption("", Texture.of(texture, imageWidth, imageHeight), null));
        return xin.vanilla.banira.api.client.ui.UiGameElement.artwork(new DropdownOption("", "", item, null, null));
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

    public void render(PoseStack graphics, Font font, int x, int y, int width, int height) {
        if (texture != null) {
            int drawWidth = Math.min(width, height * imageWidth / imageHeight);
            int drawHeight = drawWidth * imageHeight / imageWidth;
            xin.vanilla.banira.client.util.AbstractGuiUtils.blitBlend(graphics, texture,
                    x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                    drawWidth, drawHeight, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            return;
        }
        if (width >= 16 && height >= 16) xin.vanilla.banira.client.gui.widget.ItemWidget.renderItem(font, item,
                x + (width - 16) / 2, y + (height - 16) / 2, false);
    }
}
