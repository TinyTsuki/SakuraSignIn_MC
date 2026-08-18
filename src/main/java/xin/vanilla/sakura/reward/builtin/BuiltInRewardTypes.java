package xin.vanilla.sakura.reward.builtin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.reward.RewardManager;

import java.util.Collections;
import java.util.Optional;

/**
 * 内置奖励与第三方奖励通过同一公共注册入口安装。
 */
public final class BuiltInRewardTypes {
    private BuiltInRewardTypes() {
    }

    public static void register() {
        registerItem();
        registerEffect();
        registerInteger(SakuraRewardTypes.EXPERIENCE_POINT, "expPoint", 3,
                (context, value) -> {
                    context.player().giveExperiencePoints(value);
                    return RewardGrantResult.success();
                });
        registerInteger(SakuraRewardTypes.EXPERIENCE_LEVEL, "expLevel", 4,
                (context, value) -> {
                    context.player().giveExperienceLevels(value);
                    return RewardGrantResult.success();
                });
        registerInteger(SakuraRewardTypes.SIGN_IN_CARD, "signInCard", 5,
                (context, value) -> {
                    context.addSignInCards(value);
                    return RewardGrantResult.success();
                });
        registerAdvancement();
        registerMessage();
        registerCommand();
    }

    private static void registerItem() {
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.ITEM,
                        new ItemRewardCodec(), (context, value) ->
                                RewardManager.giveItemStack(context.player(), value.copy(), true)
                                        ? RewardGrantResult.success()
                                        : RewardGrantResult.of(RewardGrantStatus.REJECTED, "inventory_rejected"))
                .validator(value -> value == null || value.isEmpty() || value.getCount() <= 0
                        ? Collections.singletonList(new RewardViolation("item", "non_empty"))
                        : Collections.emptyList())
                .describer((language, value, withAmount) -> SakuraComponent.get()
                        .literal(value.getHoverName().getString())
                        .append(withAmount ? "x" + value.getCount() : ""))
                .merger(new RewardMerger<ItemStack>() {
                    @Override
                    public RewardMergeKey key(ItemStack value) {
                        String id = BuiltInRegistries.ITEM.getKey(value.getItem()).toString();
                        return RewardMergeKey.of(id + (value.hasTag() ? value.getTag().toString() : ""));
                    }

                    @Override
                    public Optional<ItemStack> merge(ItemStack first, ItemStack second) {
                        ItemStack merged = first.copy();
                        merged.setCount(first.getCount() + second.getCount());
                        return Optional.of(merged);
                    }
                })
                .addPermission(permission(SakuraRewardTypes.ITEM, 0))
                .build());
    }

    private static void registerEffect() {
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.EFFECT,
                        new EffectRewardCodec(), (context, value) -> {
                            context.player().addEffect(new MobEffectInstance(value));
                            return RewardGrantResult.success();
                        })
                .validator(value -> value == null || value.getDuration() <= 0
                        ? Collections.singletonList(new RewardViolation("duration", "positive"))
                        : Collections.emptyList())
                .describer((language, value, withAmount) -> typeName(language, 2)
                        .append(": ").append(SakuraComponent.get().object(value.getEffect().getDisplayName())))
                .merger(new RewardMerger<MobEffectInstance>() {
                    @Override
                    public RewardMergeKey key(MobEffectInstance value) {
                        return RewardMergeKey.of(BuiltInRegistries.MOB_EFFECT.getKey(value.getEffect())
                                + ":" + value.getAmplifier());
                    }

                    @Override
                    public Optional<MobEffectInstance> merge(MobEffectInstance first, MobEffectInstance second) {
                        return Optional.of(new MobEffectInstance(first.getEffect(),
                                first.getDuration() + second.getDuration(), first.getAmplifier()));
                    }
                })
                .addPermission(permission(SakuraRewardTypes.EFFECT, 0))
                .build());
    }

    private static void registerInteger(RewardTypeId id, String field, int translationCode,
                                        RewardExecutor<Integer> executor) {
        SakuraRewards.register(RewardTypeDefinition.builder(id, new IntegerRewardCodec(field), executor)
                .validator(value -> value == null || value <= 0
                        ? Collections.singletonList(new RewardViolation(field, "positive"))
                        : Collections.emptyList())
                .describer((language, value, withAmount) -> typeName(language, translationCode)
                        .append(withAmount ? "x" + value : ""))
                .merger(new RewardMerger<Integer>() {
                    @Override
                    public RewardMergeKey key(Integer value) {
                        return RewardMergeKey.of(field);
                    }

                    @Override
                    public Optional<Integer> merge(Integer first, Integer second) {
                        return Optional.of(first + second);
                    }
                })
                .addPermission(permission(id, 0))
                .build());
    }

    private static void registerAdvancement() {
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.ADVANCEMENT,
                        new AdvancementRewardCodec(), (context, value) -> {
                            Advancement advancement = context.player().server.getAdvancements().getAdvancement(value);
                            if (advancement == null) {
                                return RewardGrantResult.of(RewardGrantStatus.REJECTED, "unknown_advancement");
                            }
                            AdvancementProgress progress = context.player().getAdvancements()
                                    .getOrStartProgress(advancement);
                            progress.getRemainingCriteria().forEach(criterion ->
                                    context.player().getAdvancements().award(advancement, criterion));
                            return RewardGrantResult.success();
                        })
                .validator(value -> value == null
                        ? Collections.singletonList(new RewardViolation("advancement", "required"))
                        : Collections.emptyList())
                .describer((language, value, withAmount) -> typeName(language, 6)
                        .append(": ").append(value.toString()))
                .addPermission(permission(SakuraRewardTypes.ADVANCEMENT, 0))
                .build());
    }

    private static void registerMessage() {
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.MESSAGE,
                        new MessageRewardCodec(), (context, value) -> {
                            SakuraMessages.send(context.player(), value);
                            return RewardGrantResult.success();
                        })
                .validator(RewardValidator.acceptAll())
                .describer((language, value, withAmount) -> typeName(language, 7))
                .addPermission(permission(SakuraRewardTypes.MESSAGE, 0))
                .build());
    }

    private static void registerCommand() {
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.COMMAND,
                        new CommandRewardCodec(), (context, value) -> {
                            String command = value.replace("@s", context.player().getName().getString());
                            net.minecraft.commands.Commands commands = context.player().server.getCommands();
                            net.minecraft.commands.CommandSourceStack source = context.player()
                                    .createCommandSourceStack().withSuppressedOutput()
                                    .withPermission(CommonConfig.get().permission().permissionCommandReward());
                            commands.performCommand(commands.getDispatcher().parse(command, source), command);
                            return RewardGrantResult.success();
                        })
                .validator(value -> value == null || value.trim().isEmpty()
                        ? Collections.singletonList(new RewardViolation("command", "non_blank"))
                        : Collections.emptyList())
                .describer((language, value, withAmount) -> typeName(language, 8))
                .addPermission(permission(SakuraRewardTypes.COMMAND, 2))
                .build());
    }

    private static RewardAddPermission permission(RewardTypeId type, int level) {
        return RewardAddPermission.of(level,
                "sakura_sign_in:reward.add." + type.getPath());
    }

    private static Component typeName(String languageCode, int legacyTranslationCode) {
        return SakuraComponent.get().transLang(languageCode, "word",
                "reward_type_" + legacyTranslationCode);
    }
}
