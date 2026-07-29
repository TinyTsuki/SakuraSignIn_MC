package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.text.SakuraComponent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.awt.Color;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * 兑换码领取与失败次数记录。
 */
public final class CdkCommand {
    private CdkCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal(CommonConfig.get().command().commandCdk())
                .then(Commands.argument("key", StringArgumentType.greedyString())
                        .executes(context -> execute(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "key")
                        )));
    }

    private static int execute(ServerPlayerEntity player, String key) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        if (failedToday(data) >= 5) {
            send(player, "cdk_error_too_many_times", 0xFFFF0000);
            return 1;
        }
        if (alreadyReceived(data, key)) {
            send(player, "cdk_already_received", 0xFFFFFF00);
            return 1;
        }

        List<KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>>> rewards =
                RewardConfigManager.getRewardConfig().getCdkRewards();
        int index = rewards == null ? -1 : IntStream.range(0, rewards.size())
                .filter(i -> rewards.get(i).getKey().getKey().equals(key))
                .filter(i -> rewards.get(i).getValue().getValue().get() > 0)
                .findFirst()
                .orElse(-1);
        boolean success = false;
        if (index < 0) {
            send(player, "cdk_not_exist_or_already_received", 0xFFFF0000);
        } else {
            KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> reward =
                    takeReward(rewards, index);
            RewardConfigManager.saveRewardOption();
            Date expiresAt = DateUtils.format(reward.getKey().getValue());
            if (expiresAt.before(DateUtils.getServerDate())) {
                send(player, "cdk_expired", 0xFFFF0000);
            } else {
                Component message = SakuraComponent.get().trans(player, "message", "receive_reward_success"
                );
                reward.getValue().getKey().forEach(entry -> {
                    Component detail = entry.getName(SakuraUtils.getPlayerLanguage(player), true);
                    detail.color(RewardManager.giveRewardToPlayer(player, data, entry)
                            ? Color.GREEN.getRGB()
                            : Color.RED.getRGB());
                    message.append(", ").append(detail);
                });
                SakuraMessages.send(player, message, SakuraNotificationTypes.CDK);
                success = true;
            }
        }
        data.getCdkRecords().add(new KeyValue<>(
                key, new KeyValue<>(DateUtils.getServerDate(), success)
        ));
        SakuraPlayerData.saveAndSync(player);
        return 1;
    }

    private static long failedToday(IPlayerSignInData data) {
        int today = DateUtils.toDateInt(DateUtils.getServerDate());
        return data.getCdkRecords().stream()
                .filter(record -> DateUtils.toDateInt(record.getValue().getKey()) == today)
                .filter(record -> !record.getValue().getValue())
                .count();
    }

    private static boolean alreadyReceived(IPlayerSignInData data, String key) {
        return data.getCdkRecords().stream()
                .filter(record -> record.getKey().equals(key))
                .anyMatch(record -> record.getValue().getValue());
    }

    private static KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> takeReward(
            List<KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>>> rewards,
            int index
    ) {
        KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> value =
                rewards.get(index);
        if (value.getValue().getValue().get() <= 1) {
            return rewards.remove(index);
        }
        value.getValue().getValue().decrementAndGet();
        return new KeyValue<>(
                value.getKey(),
                new KeyValue<>(value.getValue().getKey(), new AtomicInteger(1))
        );
    }

    private static void send(ServerPlayerEntity player, String key, int color) {
        SakuraMessages.send(
                player,
                SakuraComponent.get().trans(player, "message", key).color(color),
                SakuraNotificationTypes.CDK
        );
    }
}
