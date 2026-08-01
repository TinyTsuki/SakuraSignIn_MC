package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.api.reward.client.RewardClientExtension;
import xin.vanilla.sakura.api.reward.client.RewardDisplayContext;
import xin.vanilla.sakura.api.reward.client.RewardPresentation;
import xin.vanilla.sakura.api.reward.client.RewardRenderContext;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.reward.RewardOperations;

/**
 * 内置客户端展示与第三方展示使用同一个注册入口。
 */
public final class BuiltInRewardClientTypes {
    private BuiltInRewardClientTypes() {
    }

    public static void register() {
        register(SakuraRewardTypes.ITEM, 10, new BasePresentation<ItemStack>() {
            @Override
            public void renderIcon(RewardRenderContext context, ItemStack value) {
                context.drawItem(value);
            }
        });
        register(SakuraRewardTypes.EFFECT, 20, new BasePresentation<EffectInstance>() {
            @Override
            public void renderIcon(RewardRenderContext context, EffectInstance value) {
                context.drawEffect(value);
            }
        });
        registerNumeric(SakuraRewardTypes.EXPERIENCE_POINT, 30, "point");
        registerNumeric(SakuraRewardTypes.EXPERIENCE_LEVEL, 40, "level");
        registerNumeric(SakuraRewardTypes.SIGN_IN_CARD, 50, "card");
        register(SakuraRewardTypes.ADVANCEMENT, 60, new BasePresentation<ResourceLocation>() {
            @Override
            public void renderIcon(RewardRenderContext context, ResourceLocation value) {
                AdvancementData data = SakuraClientState.getAdvancementData().stream()
                        .filter(candidate -> candidate.getId().equals(value))
                        .findFirst().orElse(null);
                if (data == null || data.getDisplayInfo() == null) {
                    context.drawPlaceholder(context.reward().getTypeId().toString());
                    return;
                }
                context.drawItem(data.getDisplayInfo().getIcon());
            }
        });
        register(SakuraRewardTypes.MESSAGE, 70, new BasePresentation<Component>() {
            @Override
            public void renderIcon(RewardRenderContext context, Component value) {
                context.drawBuiltInIcon("message");
            }
        });
        register(SakuraRewardTypes.COMMAND, 80, new BasePresentation<String>() {
            @Override
            public void renderIcon(RewardRenderContext context, String value) {
                context.drawItem(new ItemStack(Items.REPEATING_COMMAND_BLOCK));
            }
        });
    }

    private static void registerNumeric(RewardTypeId typeId, int sortOrder, String icon) {
        register(typeId, sortOrder, new BasePresentation<Integer>() {
            @Override
            public void renderIcon(RewardRenderContext context, Integer value) {
                context.drawBuiltInIcon(icon);
                if (context.withAmount()) {
                    context.drawAmount(String.valueOf(value));
                }
            }
        });
    }

    private static <T> void register(RewardTypeId typeId, int sortOrder,
                                     RewardPresentation<T> presentation) {
        SakuraRewardClient.register(typeId, RewardClientExtension.builder(presentation)
                .sortOrder(sortOrder)
                .build());
    }

    private abstract static class BasePresentation<T> implements RewardPresentation<T> {
        @Override
        public xin.vanilla.banira.common.data.Component displayName(
                RewardDisplayContext context, T value) {
            return RewardOperations.describe(context.languageCode(), context.reward(),
                    context.withAmount());
        }
    }
}
