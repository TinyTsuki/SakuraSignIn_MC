package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.banira.client.gui.widget.EffectIconWidget;
import xin.vanilla.banira.client.gui.widget.ItemWidget;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraLang;
import xin.vanilla.sakura.api.reward.client.RewardRenderContext;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 奖励图标由客户端类型注册表分派，单个扩展失败时只回退该图标。
 */
public final class RewardRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int ICON_SIZE = 16;

    private RewardRenderer() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static UiGameElement uiIcon(Reward reward) {
        UiIconContext context = new UiIconContext(reward);
        try {
            SakuraRewardClient.Registration registration = SakuraRewardClient.find(reward.getTypeId()).orElse(null);
            if (registration != null)
                registration.getExtension().getPresentation().renderIcon(context, RewardOperations.decode(reward));
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Reward icon failed for {}", reward.getTypeId(), failure);
        }
        return context.icon;
    }

    private static final class UiIconContext implements RewardRenderContext {
        private final Reward reward;
        private UiGameElement icon = UiGameElement.option(new DropdownOption("", new ItemStack(Items.BARRIER), null));

        private UiIconContext(Reward reward) {
            this.reward = reward;
        }

        public Reward reward() {
            return reward;
        }

        public String languageCode() {
            return SakuraLang.getClientLanguage();
        }

        public boolean withAmount() {
            return true;
        }

        public int x() {
            return 0;
        }

        public int y() {
            return 0;
        }

        public int size() {
            return ICON_SIZE;
        }

        public void drawItem(Object stack) {
            if (stack instanceof ItemStack)
                icon = UiGameElement.option(new DropdownOption("", ((ItemStack) stack).copy(), null));
        }

        public void drawEffect(Object effect) {
            if (effect instanceof MobEffectInstance) icon = UiGameElement.effect((MobEffectInstance) effect);
        }

        public void drawBuiltInIcon(String id) {
            if (com.google.common.collect.ImmutableSet.of("point", "level", "card", "message").contains(id))
                icon = UiGameElement.sprite(xin.vanilla.sakura.client.theme.SakuraThemeTextures.get("reward." + id).texture());
        }

        public void drawAmount(String text) {
        }

        public void drawPlaceholder(String id) {
        }
    }

    public static void renderCustomReward(PoseStack stack, ItemRenderer itemRenderer,
                                          Font font, ResourceLocation texture,
                                          TextureCoordinate coordinates, Reward reward,
                                          int x, int y, boolean showText) {
        renderCustomReward(stack, itemRenderer, font, texture, coordinates,
                reward, x, y, showText, true);
    }

    public static void renderCustomReward(PoseStack stack, ItemRenderer itemRenderer,
                                          Font font, ResourceLocation texture,
                                          TextureCoordinate coordinates, Reward reward,
                                          int x, int y, boolean showText,
                                          boolean showQuality) {
        NativeRenderContext context = new NativeRenderContext(stack, itemRenderer, font,
                texture, coordinates, reward, x, y, showText);
        Optional<SakuraRewardClient.Registration<?>> registration =
                SakuraRewardClient.find(reward.getTypeId());
        if (registration.isPresent()) {
            renderResolved(registration.get(), context, reward);
        } else {
            context.drawPlaceholder(reward.getTypeId().toString());
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderResolved(SakuraRewardClient.Registration registration,
                                       NativeRenderContext context, Reward reward) {
        try {
            Object value = RewardOperations.decode(reward);
            registration.getExtension().getPresentation().renderIcon(context, value);
        } catch (Throwable exception) {
            LOGGER.warn("Reward presentation failed for {}", reward.getTypeId(), exception);
            context.drawPlaceholder(reward.getTypeId().toString());
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

    private static final class NativeRenderContext implements RewardRenderContext {
        private final PoseStack stack;
        private final ItemRenderer itemRenderer;
        private final Font font;
        private final ResourceLocation texture;
        private final TextureCoordinate coordinates;
        private final Reward reward;
        private final int x;
        private final int y;
        private final boolean showAmount;

        private NativeRenderContext(PoseStack stack, ItemRenderer itemRenderer,
                                    Font font, ResourceLocation texture,
                                    TextureCoordinate coordinates, Reward reward,
                                    int x, int y, boolean showAmount) {
            this.stack = stack;
            this.itemRenderer = itemRenderer;
            this.font = font;
            this.texture = texture;
            this.coordinates = coordinates;
            this.reward = reward;
            this.x = x;
            this.y = y;
            this.showAmount = showAmount;
        }

        @Override
        public Reward reward() {
            return reward;
        }

        @Override
        public String languageCode() {
            return Minecraft.getInstance().options.languageCode;
        }

        @Override
        public boolean withAmount() {
            return showAmount;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int size() {
            return ICON_SIZE;
        }

        @Override
        public void drawItem(Object itemStack) {
            if (itemStack instanceof ItemStack) {
                ItemWidget.renderItem(itemRenderer, font, (ItemStack) itemStack,
                        x, y, showAmount);
            } else {
                drawPlaceholder(reward.getTypeId().toString());
            }
        }

        @Override
        public void drawEffect(Object effectInstance) {
            if (effectInstance instanceof MobEffectInstance) {
                EffectIconWidget.drawEffectIcon(stack, font, (MobEffectInstance) effectInstance,
                        x, y, ICON_SIZE, ICON_SIZE, showAmount);
            } else {
                drawPlaceholder(reward.getTypeId().toString());
            }
        }

        @Override
        public void drawBuiltInIcon(String iconId) {
            if (!com.google.common.collect.ImmutableSet.of("point", "level", "card", "message").contains(iconId)) {
                drawPlaceholder(reward.getTypeId().toString());
                return;
            }
            xin.vanilla.sakura.client.theme.SakuraThemeTextures.get("reward." + iconId).draw(stack, x, y, ICON_SIZE, ICON_SIZE);
        }

        @Override
        public void drawAmount(String text) {
            if (!showAmount || text == null) return;
            int width = font.width(text);
            font.drawShadow(stack, text, x + ICON_SIZE - width / 2.0F - 2,
                    y + ICON_SIZE - font.lineHeight + 2, 0xFFFFFFFF);
        }

        @Override
        public void drawPlaceholder(String typeId) {
            ItemWidget.renderItem(itemRenderer, font, new ItemStack(Items.BARRIER),
                    x, y, false);
        }
    }
}
