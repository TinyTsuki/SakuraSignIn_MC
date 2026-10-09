package xin.vanilla.sakura.reward.personaldate;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.banira.common.data.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.awt.Color;

/** 服务端玩家实体与纯日期奖励服务之间的薄适配。 */
public final class PersonalDateRewardDispatcher {
    private static final PersonalDateRewardService SERVICE = new PersonalDateRewardService();

    private PersonalDateRewardDispatcher() {
    }

    public static List<PersonalDateRewardSnapshot> reserveSignIn(ServerPlayerEntity player, Date currentDate) {
        return SERVICE.reserveForSignIn(currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                RewardConfigManager.getRewardConfig().getPersonalDatePresets(), SakuraPlayerData.get(player));
    }

    public static PersonalDateDeliveryResult deliverOnline(ServerPlayerEntity player,
                                                           Date currentDate) {
        return deliver(player, PersonalDateDeliveryMode.ONLINE, currentDate);
    }

    private static PersonalDateDeliveryResult deliver(ServerPlayerEntity player,
                                                      PersonalDateDeliveryMode mode,
                                                      Date currentDate) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LocalDate day = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        List<PersonalDatePreset> presets = RewardConfigManager.getRewardConfig().getPersonalDatePresets();
        List<PersonalDateRewardReceipt> receipts = new ArrayList<>();
        PersonalDateDeliveryResult result = SERVICE.deliver(
                mode,
                day,
                presets,
                data,
                (reward, occurrence) -> {
                    boolean granted = RewardManager.giveRewardToPlayer(
                        player,
                        data,
                        reward,
                        Date.from(occurrence.getTargetDate().atStartOfDay(
                                ZoneId.systemDefault()).toInstant()),
                        "personal_date:" + occurrence.getPresetId());
                    if (granted) {
                        String name = presets.stream().filter(preset -> preset.getId().equals(occurrence.getPresetId()))
                                .map(PersonalDatePreset::getDisplayName).findFirst().orElse(occurrence.getPresetId());
                        receipts.add(new PersonalDateRewardReceipt(name, reward));
                    }
                }
        );
        return new PersonalDateDeliveryResult(result.getClaimedOccurrences(), result.getProcessedRewards(), receipts);
    }

    public static void sendDetails(ServerPlayerEntity player, PersonalDateDeliveryResult result) {
        if (result.getReceipts().isEmpty()) return;
        List<Component> details = new ArrayList<>();
        for (PersonalDateRewardReceipt receipt : result.getReceipts()) {
            details.add(SakuraComponent.get().literal(receipt.getPresetName() + ": ")
                    .append(receipt.getReward().getName(SakuraUtils.getPlayerLanguage(player), true))
                    .color(Color.GREEN.getRGB()));
        }
        SakuraMessages.successBatch(player, SakuraComponent.get().trans(player,
                "word", "personal_date_reward_success"), details, SakuraNotificationTypes.REWARD);
    }
}
