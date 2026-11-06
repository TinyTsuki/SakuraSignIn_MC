package xin.vanilla.sakura.client.gui.lottery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.sakura.data.lottery.LotteryPool;

public record LotteryArtwork(ResourceLocation texture, ItemStack item) {
    private static final int WIDTH = 512, HEIGHT = 288;

    public static LotteryArtwork resolve(LotteryPool pool) {
        ResourceLocation texture = pool == null || pool.getArtworkTexture().isEmpty() ? null : ResourceLocation.tryParse(pool.getArtworkTexture());
        Minecraft client = Minecraft.getInstance();
        if (texture != null && (client == null || client.getResourceManager().getResource(texture).isEmpty()))
            texture = null;
        ResourceLocation itemId = pool == null || pool.getArtworkItem().isEmpty() ? null : ResourceLocation.tryParse(pool.getArtworkItem());
        ItemStack item = itemId == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        return new LotteryArtwork(texture, item.isEmpty() ? new ItemStack(Items.CHERRY_SAPLING) : item);
    }

    public UiGameElement element() {
        if (texture != null) return UiGameElement.sprite(Texture.of(texture, WIDTH, HEIGHT));
        return UiGameElement.option(new DropdownOption("", "", item, null, null));
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height, int itemSize) {
        if (texture != null) {
            int drawWidth = Math.min(width, height * WIDTH / HEIGHT);
            int drawHeight = drawWidth * HEIGHT / WIDTH;
            graphics.blit(texture, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                    drawWidth, drawHeight, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
            return;
        }
        int size = Math.min(itemSize, Math.min(width, height));
        if (size < 1) return;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + (width - size) / 2.0, y + (height - size) / 2.0, 0);
            pose.scale(size / 16.0F, size / 16.0F, 1);
            graphics.renderItem(item, 0, 0);
        } finally {
            pose.popPose();
        }
    }
}
