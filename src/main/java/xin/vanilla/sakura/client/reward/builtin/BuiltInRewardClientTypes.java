package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.api.reward.client.RewardClientExtension;
import xin.vanilla.sakura.api.reward.client.RewardDisplayContext;
import xin.vanilla.sakura.api.reward.client.RewardPresentation;
import xin.vanilla.sakura.api.reward.client.RewardRenderContext;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.reward.RewardOperations;

/**
 * 内置客户端展示与第三方展示使用同一个注册入口。
 */
public final class BuiltInRewardClientTypes {
    private BuiltInRewardClientTypes() {
    }

    public static void register() {
        register(SakuraRewardTypes.ITEM, 10, 1, new BasePresentation<ItemStack>() {
            @Override
            public void renderIcon(RewardRenderContext context, ItemStack value) {
                context.drawItem(value);
            }
        }, BuiltInRewardEditors.item());
        register(SakuraRewardTypes.EFFECT, 20, 2, new BasePresentation<MobEffectInstance>() {
            @Override
            public void renderIcon(RewardRenderContext context, MobEffectInstance value) {
                context.drawEffect(value);
            }
        }, BuiltInRewardEditors.effect());
        registerNumeric(SakuraRewardTypes.EXPERIENCE_POINT, 30, "point", "enter_exp_point");
        registerNumeric(SakuraRewardTypes.EXPERIENCE_LEVEL, 40, "level", "enter_exp_level");
        registerNumeric(SakuraRewardTypes.SIGN_IN_CARD, 50, "card", "enter_sign_in_card");
        register(SakuraRewardTypes.ADVANCEMENT, 60, 6, new BasePresentation<ResourceLocation>() {
            @Override
            public void renderIcon(RewardRenderContext context, ResourceLocation value) {
                context.drawItem(AdvancementPresentationResolver.resolve(value).getIcon());
            }

            @Override
            public xin.vanilla.banira.common.data.Component displayName(
                    RewardDisplayContext context, ResourceLocation value) {
                return AdvancementPresentationResolver.resolve(value).getName();
            }
        }, BuiltInRewardEditors.advancement());
        register(SakuraRewardTypes.MESSAGE, 70, 7, new BasePresentation<Component>() {
            @Override
            public void renderIcon(RewardRenderContext context, Component value) {
                context.drawBuiltInIcon("message");
            }
        }, BuiltInRewardEditors.message());
        register(SakuraRewardTypes.COMMAND, 80, 8, new BasePresentation<String>() {
            @Override
            public void renderIcon(RewardRenderContext context, String value) {
                context.drawItem(new ItemStack(Items.REPEATING_COMMAND_BLOCK));
            }
        }, BuiltInRewardEditors.command());
    }

    private static void registerNumeric(RewardTypeId typeId, int sortOrder, String icon,
                                        String titleKey) {
        int translationCode = SakuraRewardTypes.EXPERIENCE_POINT.equals(typeId) ? 3
                : SakuraRewardTypes.EXPERIENCE_LEVEL.equals(typeId) ? 4 : 5;
        register(typeId, sortOrder, translationCode, new BasePresentation<Integer>() {
            @Override
            public void renderIcon(RewardRenderContext context, Integer value) {
                context.drawBuiltInIcon(icon);
                if (context.withAmount()) {
                    context.drawAmount(String.valueOf(value));
                }
            }
        }, BuiltInRewardEditors.positiveInteger(titleKey));
    }

    private static <T> void register(RewardTypeId typeId, int sortOrder, int translationCode,
                                     RewardPresentation<T> presentation,
                                     xin.vanilla.sakura.api.reward.client.RewardEditorProvider<T> editor) {
        SakuraRewardClient.register(typeId, RewardClientExtension.builder(presentation)
                .editor(editor)
                .typeName(() -> xin.vanilla.sakura.SakuraComponent.get()
                        .transClient("word", "reward_type_" + translationCode))
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
