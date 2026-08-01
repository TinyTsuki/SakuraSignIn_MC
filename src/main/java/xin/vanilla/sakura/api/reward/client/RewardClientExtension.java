package xin.vanilla.sakura.api.reward.client;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;
import xin.vanilla.banira.common.data.Component;

@Getter
public final class RewardClientExtension<T> {
    private final RewardPresentation<T> presentation;
    @Nullable
    private final RewardEditorProvider<T> editor;
    @Nullable
    private final Supplier<Component> typeName;
    private final int sortOrder;

    private RewardClientExtension(Builder<T> builder) {
        this.presentation = builder.presentation;
        this.editor = builder.editor;
        this.typeName = builder.typeName;
        this.sortOrder = builder.sortOrder;
    }

    public static <T> Builder<T> builder(RewardPresentation<T> presentation) {
        return new Builder<>(presentation);
    }

    public static final class Builder<T> {
        private final RewardPresentation<T> presentation;
        private RewardEditorProvider<T> editor;
        private Supplier<Component> typeName;
        private int sortOrder;

        private Builder(RewardPresentation<T> presentation) {
            this.presentation = Objects.requireNonNull(presentation, "presentation");
        }

        public Builder<T> editor(RewardEditorProvider<T> value) {
            this.editor = Objects.requireNonNull(value, "editor");
            return this;
        }

        /** 类型选择器中的名称；未提供时显示 namespaced ID。 */
        public Builder<T> typeName(Supplier<Component> value) {
            this.typeName = Objects.requireNonNull(value, "typeName");
            return this;
        }

        public Builder<T> sortOrder(int value) {
            this.sortOrder = value;
            return this;
        }

        public RewardClientExtension<T> build() {
            return new RewardClientExtension<>(this);
        }
    }
}
