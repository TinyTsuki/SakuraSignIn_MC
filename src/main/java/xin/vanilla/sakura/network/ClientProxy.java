package xin.vanilla.sakura.network;

import xin.vanilla.sakura.text.SakuraComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.packet.AdvancementPacket;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.banira.common.data.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientProxy {
    public static final Logger LOGGER = LogManager.getLogger();

    public static void handleSynPlayerData(PlayerDataSyncPacket packet) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                IPlayerSignInData clientData = packet.getData();
                // 摘要刷新不能清除用户已按需加载的其他月份。
                clientData.setSignInRecords(new ArrayList<>(
                        SakuraPlayerData.get(player).getSignInRecords()
                ));
                SakuraPlayerData.setClient(player.getUUID(), clientData);
                SakuraNetwork.sendToServer(new xin.vanilla.sakura.network.packet.ClientConfigSyncPacket());
                LOGGER.debug("Client: Player data received successfully.");
            } catch (Exception ignored) {
                LOGGER.debug("Client: Player data received failed.");
            }
            SakuraSignIn.setEnabled(true);
        }
    }

    public static void handleAdvancement(AdvancementPacket packet) {
        SakuraSignIn.setAdvancementData(packet.getAdvancements());
    }

    public static void handleMonthData(PlayerMonthSyncPacket packet) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || !player.getUUID().equals(packet.getPlayerUUID())) {
            return;
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        List<SignInRecord> merged = new ArrayList<>(data.getSignInRecords());
        merged.removeIf(record -> packet.getMonth().equals(PlayerMonthSyncPacket.monthOf(record)));
        merged.addAll(packet.getRecords());
        data.setSignInRecords(merged);
    }

    public static void handleRewardOptionSync(RewardOptionSyncPacket packet) {
        try {
            RewardConfigManager.backupRewardOption();
            RewardConfigManager.setRewardConfig(RewardConfigManager.fromSyncPacketList(
                    Collections.singletonList(packet)
            ));
            RewardConfigManager.setRewardOptionDataChanged(true);
            RewardConfigManager.saveRewardOption();
            NotificationManager.get().addNotification(
                    NotificationManager.Notification.ofComponentWithBlack(
                            SakuraComponent.get().trans("message", "reward_option_download_success")
                    )
            );
        } catch (RuntimeException exception) {
            NotificationManager.get().addNotification(
                    NotificationManager.Notification.ofComponentWithBlack(
                            SakuraComponent.get().trans("message", "reward_option_download_failed")
                    ).setBgColor(0x88FF5555)
            );
            throw exception;
        }
    }

    public static void handleRewardOptionUploadResult(boolean success) {
        Component message = SakuraComponent.get().trans("message", success ? "reward_option_upload_success" : "reward_option_upload_failed"
        );
        NotificationManager.Notification notification =
                NotificationManager.Notification.ofComponentWithBlack(message);
        if (!success) {
            notification.setBgColor(0x88FF5555);
        }
        NotificationManager.get().addNotification(notification);
    }
}
