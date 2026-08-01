package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.client.gui.widget.EffectIconWidget;
import xin.vanilla.banira.client.gui.widget.ItemWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import java.math.BigDecimal;

/**
 * Sakura 奖励图标渲染器，只保留与奖励类型和主题纹理有关的逻辑。
 */
public final class RewardRenderer {
    private static final int ICON_SIZE = 16;

    private RewardRenderer() {
    }

    public static void renderCustomReward(MatrixStack stack, ItemRenderer itemRenderer,
                                          FontRenderer font, ResourceLocation texture,
                                          TextureCoordinate coordinates, Reward reward,
                                          int x, int y, boolean showText) {
        renderCustomReward(stack, itemRenderer, font, texture, coordinates,
                reward, x, y, showText, true);
    }

    public static void renderCustomReward(MatrixStack stack, ItemRenderer itemRenderer,
                                          FontRenderer font, ResourceLocation texture,
                                          TextureCoordinate coordinates, Reward reward,
                                          int x, int y, boolean showText,
                                          boolean showQuality) {
        if (reward.getType() == ERewardType.ITEM) {
            ItemWidget.renderItem(itemRenderer, font,
                    RewardManager.deserializeReward(reward), x, y, showText);
        } else if (reward.getType() == ERewardType.EFFECT) {
            EffectIconWidget.drawEffectIcon(stack, font,
                    RewardManager.deserializeReward(reward), x, y,
                    ICON_SIZE, ICON_SIZE, showText);
        } else if (reward.getType() == ERewardType.EXP_POINT) {
            drawNumericIcon(stack, font, texture, coordinates.getPointUV(),
                    coordinates, reward, x, y, showText);
        } else if (reward.getType() == ERewardType.EXP_LEVEL) {
            drawNumericIcon(stack, font, texture, coordinates.getLevelUV(),
                    coordinates, reward, x, y, showText);
        } else if (reward.getType() == ERewardType.SIGN_IN_CARD) {
            drawNumericIcon(stack, font, texture, coordinates.getCardUV(),
                    coordinates, reward, x, y, showText);
        } else if (reward.getType() == ERewardType.MESSAGE) {
            drawNumericIcon(stack, font, texture, coordinates.getMessageUV(),
                    coordinates, reward, x, y, false);
        } else if (reward.getType() == ERewardType.ADVANCEMENT) {
            renderAdvancement(itemRenderer, reward, x, y);
        } else if (reward.getType() == ERewardType.COMMAND) {
            ItemWidget.renderItem(itemRenderer, font,
                    new ItemStack(Items.REPEATING_COMMAND_BLOCK), x, y, false);
        }

        if (showText && showQuality
                && reward.getProbability().compareTo(BigDecimal.ONE) != 0) {
            stack.pushPose();
            stack.translate(0, 0, 250);
            int color = probabilityColor(reward.getProbability().doubleValue());
            font.drawShadow(stack, SakuraComponent.get().literal("?").color(color).toVanilla(),
                    x - 1, y - 1, color);
            stack.popPose();
        }
    }

    private static void drawNumericIcon(MatrixStack stack, FontRenderer font,
                                        ResourceLocation texture, Coordinate uv,
                                        TextureCoordinate coordinates, Reward reward,
                                        int x, int y, boolean showText) {
        AbstractGuiUtils.blit(stack, texture, x, y, ICON_SIZE, ICON_SIZE,
                uv.getU0(), uv.getV0(), (int) uv.getUWidth(), (int) uv.getVHeight(),
                coordinates.getTotalWidth(), coordinates.getTotalHeight());
        if (!showText) {
            return;
        }
        String count = String.valueOf((Integer) RewardManager.deserializeReward(reward));
        int width = font.width(count);
        font.drawShadow(stack, count, x + ICON_SIZE - width / 2.0F - 2,
                y + ICON_SIZE - font.lineHeight + 2, 0xFFFFFFFF);
    }

    private static void renderAdvancement(ItemRenderer itemRenderer, Reward reward,
                                          int x, int y) {
        ResourceLocation id = RewardManager.deserializeReward(reward);
        AdvancementData data = SakuraClientState.getAdvancementData().stream()
                .filter(value -> value.getId().equals(id))
                .findFirst().orElse(null);
        if (data != null && data.getDisplayInfo() != null) {
            itemRenderer.renderGuiItem(data.getDisplayInfo().getIcon(), x, y);
        }
    }

    private static int probabilityColor(double probability) {
        if (probability == 1) return 0x00FFFFFF;
        if (probability >= 0.9) return 0xEFA9A9A9;
        if (probability >= 0.8) return 0xEFC0C0C0;
        if (probability >= 0.7) return 0xEFFFFFFF;
        if (probability >= 0.6) return 0xEF32CD32;
        if (probability >= 0.5) return 0xEF228B22;
        if (probability >= 0.4) return 0xEF1E90FF;
        if (probability >= 0.3) return 0xEF4682B4;
        if (probability >= 0.2) return 0xEFA020F0;
        if (probability >= 0.1) return 0xEFFFD700;
        return probability > 0 ? 0xEFFF4500 : 0xFF000000;
    }
}
