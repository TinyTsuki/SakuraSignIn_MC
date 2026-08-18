package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.data.time.SakuraClock;

import xin.vanilla.sakura.SakuraComponent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.DateUtils;
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

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(CommonConfig.get().command().commandCdk())
                .then(Commands.argument("key", StringArgumentType.greedyString())
                        .executes(context -> execute(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "key")
                        )));
    }

    private static int execute(ServerPlayer player, String key) {
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
                .filter(i -> rewards.get(i).key().key().equals(key))
                .filter(i -> rewards.get(i).value().value().get() > 0)
                .findFirst()
                .orElse(-1);
        boolean success = false;
        if (index < 0) {
            send(player, "cdk_not_exist_or_already_received", 0xFFFF0000);
        } else {
            KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> reward =
                    takeReward(rewards, index);
            RewardConfigManager.saveRewardOption();
            Date expiresAt = DateUtils.format(reward.key().value());
            if (expiresAt.before(SakuraClock.serverNow())) {
                send(player, "cdk_expired", 0xFFFF0000);
            } else {
                Component message = SakuraComponent.get().trans(player, "word", "receive_reward_success"
                );
                reward.value().key().forEach(entry -> {
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
                key, new KeyValue<>(SakuraClock.serverNow(), success)
        ));
        SakuraPlayerData.saveAndSync(player);
        return 1;
    }

    private static long failedToday(IPlayerSignInData data) {
        int today = DateUtils.toDateInt(SakuraClock.serverNow());
        return data.getCdkRecords().stream()
                .filter(record -> DateUtils.toDateInt(record.value().key()) == today)
                .filter(record -> !record.value().value())
                .count();
    }

    private static boolean alreadyReceived(IPlayerSignInData data, String key) {
        return data.getCdkRecords().stream()
                .filter(record -> record.key().equals(key))
                .anyMatch(record -> record.value().value());
    }

    private static KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> takeReward(
            List<KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>>> rewards,
            int index
    ) {
        KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> value =
                rewards.get(index);
        if (value.value().value().get() <= 1) {
            return rewards.remove(index);
        }
        value.value().value().decrementAndGet();
        return new KeyValue<>(
                value.key(),
                new KeyValue<>(value.value().key(), new AtomicInteger(1))
        );
    }

    private static void send(ServerPlayer player, String key, int color) {
        SakuraMessages.send(
                player,
                SakuraComponent.get().trans(player, "word", key).color(color),
                SakuraNotificationTypes.CDK
        );
    }
}
