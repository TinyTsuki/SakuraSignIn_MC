package xin.vanilla.sakura.reward.personaldate;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.reward.RewardManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/** 服务端玩家实体与纯日期奖励服务之间的薄适配。 */
public final class PersonalDateRewardDispatcher {
    private static final PersonalDateRewardService SERVICE = new PersonalDateRewardService();

    private PersonalDateRewardDispatcher() {
    }

    public static PersonalDateDeliveryResult deliverSignIn(ServerPlayer player,
                                                           Date currentDate) {
        return deliver(player, PersonalDateDeliveryMode.SIGN_IN, currentDate);
    }

    public static PersonalDateDeliveryResult deliverOnline(ServerPlayer player,
                                                           Date currentDate) {
        return deliver(player, PersonalDateDeliveryMode.ONLINE, currentDate);
    }

    private static PersonalDateDeliveryResult deliver(ServerPlayer player,
                                                      PersonalDateDeliveryMode mode,
                                                      Date currentDate) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LocalDate day = currentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return SERVICE.deliver(
                mode,
                day,
                RewardConfigManager.getRewardConfig().getPersonalDatePresets(),
                data,
                (reward, occurrence) -> RewardManager.giveRewardToPlayer(
                        player,
                        data,
                        reward,
                        Date.from(occurrence.getTargetDate().atStartOfDay(
                                ZoneId.systemDefault()).toInstant()),
                        "personal_date:" + occurrence.getPresetId()
                )
        );
    }
}
