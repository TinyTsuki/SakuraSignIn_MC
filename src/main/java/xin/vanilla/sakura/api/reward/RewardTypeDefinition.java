package xin.vanilla.sakura.api.reward;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 将一种奖励的公共侧能力组合成单个注册单元。
 */
@Getter
public final class RewardTypeDefinition<T> {
    private final RewardTypeId id;
    private final RewardCodec<T> codec;
    private final RewardValidator<T> validator;
    private final RewardExecutor<T> executor;
    @Nullable
    private final RewardMerger<T> merger;
    private final RewardAddPermission addPermission;

    private RewardTypeDefinition(Builder<T> builder) {
        this.id = builder.id;
        this.codec = builder.codec;
        this.validator = Objects.requireNonNull(builder.validator, "validator");
        this.executor = builder.executor;
        this.merger = builder.merger;
        this.addPermission = Objects.requireNonNull(builder.addPermission, "addPermission");
    }

    public static <T> Builder<T> builder(RewardTypeId id, RewardCodec<T> codec,
                                         RewardExecutor<T> executor) {
        return new Builder<>(id, codec, executor);
    }

    public static final class Builder<T> {
        private final RewardTypeId id;
        private final RewardCodec<T> codec;
        private final RewardExecutor<T> executor;
        private RewardValidator<T> validator;
        private RewardMerger<T> merger;
        private RewardAddPermission addPermission;

        private Builder(RewardTypeId id, RewardCodec<T> codec, RewardExecutor<T> executor) {
            this.id = Objects.requireNonNull(id, "id");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        public Builder<T> validator(RewardValidator<T> value) {
            this.validator = Objects.requireNonNull(value, "validator");
            return this;
        }

        public Builder<T> merger(RewardMerger<T> value) {
            this.merger = Objects.requireNonNull(value, "merger");
            return this;
        }

        public Builder<T> addPermission(RewardAddPermission value) {
            this.addPermission = Objects.requireNonNull(value, "addPermission");
            return this;
        }

        public RewardTypeDefinition<T> build() {
            return new RewardTypeDefinition<>(this);
        }
    }
}
