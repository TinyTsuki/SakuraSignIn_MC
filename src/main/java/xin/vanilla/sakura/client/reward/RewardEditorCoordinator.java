package xin.vanilla.sakura.client.reward;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;
import com.mojang.blaze3d.matrix.MatrixStack;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.client.RewardClientExtension;
import xin.vanilla.sakura.api.reward.client.RewardEditContext;
import xin.vanilla.sakura.api.reward.client.RewardEditorProvider;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.gui.RewardProbabilityFlow;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * 统一奖励内容编辑、概率编辑和界面返回，业务界面只处理最终 Reward。
 */
public final class RewardEditorCoordinator {
    private RewardEditorCoordinator() {
    }

    public static boolean openCreate(Screen parent, RewardTypeId typeId,
                                     Consumer<Reward> onSubmit) {
        SakuraRewardClient.Registration<?> registration = SakuraRewardClient.find(typeId)
                .orElse(null);
        if (registration == null || registration.getExtension().getEditor() == null) {
            return false;
        }
        return openCreateResolved(parent, registration, onSubmit);
    }

    public static boolean openEdit(Screen parent, Reward reward,
                                   Consumer<Reward> onSubmit) {
        if (reward == null) {
            return false;
        }
        SakuraRewardClient.Registration<?> registration = SakuraRewardClient
                .find(reward.getTypeId()).orElse(null);
        if (registration == null || registration.getExtension().getEditor() == null) {
            return false;
        }
        return openEditResolved(parent, registration, reward, onSubmit);
    }

    public static boolean openProbability(Screen parent, Reward reward,
                                          Consumer<Reward> onSubmit) {
        if (reward == null) {
            return false;
        }
        Minecraft.getInstance().setScreen(RewardProbabilityFlow.create(
                parent,
                reward.getProbability(),
                probability -> reward.clone().setProbability(probability),
                onSubmit
        ));
        return true;
    }

    /**
     * 多步表单以此页为返回点；提交后继续，取消则回到原界面。
     */
    public static Screen deferred(Screen parent, Runnable continuation,
                                  java.util.function.BooleanSupplier shouldContinue) {
        return new DeferredScreen(parent, continuation, shouldContinue);
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean openCreateResolved(
            Screen parent, SakuraRewardClient.Registration<?> raw,
            Consumer<Reward> onSubmit) {
        SakuraRewardClient.Registration<T> registration =
                (SakuraRewardClient.Registration<T>) raw;
        RewardEditorProvider<T> editor = registration.getExtension().getEditor();
        if (editor == null) {
            return false;
        }
        ValueTransitionScreen<T> transition = new ValueTransitionScreen<>(parent, value -> {
            Reward draft = new Reward(value, registration.getTypeId(), BigDecimal.ONE);
            Minecraft.getInstance().setScreen(RewardProbabilityFlow.create(
                    parent,
                    BigDecimal.ONE,
                    probability -> draft.clone().setProbability(probability),
                    onSubmit
            ));
        });
        editor.open(new RewardEditContext<>(transition, null,
                transition::submit, transition::cancel));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean openEditResolved(
            Screen parent, SakuraRewardClient.Registration<?> raw, Reward original,
            Consumer<Reward> onSubmit) {
        SakuraRewardClient.Registration<T> registration =
                (SakuraRewardClient.Registration<T>) raw;
        RewardClientExtension<T> extension = registration.getExtension();
        RewardEditorProvider<T> editor = extension.getEditor();
        if (editor == null) {
            return false;
        }
        final T initial;
        try {
            initial = RewardOperations.decode(original);
        } catch (RuntimeException exception) {
            return false;
        }
        ValueTransitionScreen<T> transition = new ValueTransitionScreen<>(parent, value -> {
            Reward updated = new Reward(value, registration.getTypeId(),
                    original.getProbability());
            updated.setRewarded(original.isRewarded()).setDisabled(original.isDisabled());
            onSubmit.accept(updated);
            Minecraft.getInstance().setScreen(parent);
        });
        editor.open(new RewardEditContext<>(transition, initial,
                transition::submit, transition::cancel));
        return true;
    }

    private static final class ValueTransitionScreen<T> extends BaniraScreen {
        private final Screen parent;
        private final Consumer<T> continuation;
        @Nullable
        private T value;
        private boolean submitted;

        private ValueTransitionScreen(Screen parent, Consumer<T> continuation) {
            super(new StringTextComponent(""));
            this.parent = parent;
            this.continuation = continuation;
            previousScreen(parent);
            BaniraScreen.inheritThemeAndSeason(this, parent, null, null);
        }

        private void submit(T value) {
            this.value = value;
            this.submitted = true;
        }

        private void cancel() {
            this.submitted = false;
        }

        @Override
        protected void initWidgets() {
            if (submitted) {
                continuation.accept(value);
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }

        @Override
        protected void onRender(MatrixStack stack, float partialTicks) {
        }
    }

    private static final class DeferredScreen extends BaniraScreen {
        private final Screen parent;
        private final Runnable continuation;
        private final java.util.function.BooleanSupplier shouldContinue;

        private DeferredScreen(Screen parent, Runnable continuation,
                               java.util.function.BooleanSupplier shouldContinue) {
            super(new StringTextComponent(""));
            this.parent = parent;
            this.continuation = continuation;
            this.shouldContinue = shouldContinue;
            previousScreen(parent);
            BaniraScreen.inheritThemeAndSeason(this, parent, null, null);
        }

        @Override
        protected void initWidgets() {
            if (shouldContinue.getAsBoolean()) {
                continuation.run();
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }

        @Override
        protected void onRender(MatrixStack stack, float partialTicks) {
        }
    }
}
