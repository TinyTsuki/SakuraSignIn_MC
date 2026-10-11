package xin.vanilla.sakura.network;

import xin.vanilla.sakura.SakuraComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.packet.AdvancementPacket;
import xin.vanilla.sakura.network.packet.ClientConfigSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.network.month.MonthDataTransfer;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.network.packet.PersonalDatePresetSyncPacket;
import xin.vanilla.sakura.network.packet.CommonConfigSnapshotPacket;
import xin.vanilla.sakura.network.packet.LotteryPoolSyncPacket;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.data.lottery.LotteryPools;
import xin.vanilla.sakura.screen.LotteryRevealScreen;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresets;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.banira.common.data.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClientProxy {
    public static final Logger LOGGER = LogManager.getLogger();
    private static final MonthDataTransfer.Receiver MONTHS = new MonthDataTransfer.Receiver(System::nanoTime);
    private static final xin.vanilla.sakura.network.lottery.LotteryPoolReceiver LOTTERY =
            new xin.vanilla.sakura.network.lottery.LotteryPoolReceiver(System::nanoTime);
    public static void expireLotteryTransfers() { LOTTERY.expire(); }
    public static void clearLotteryTransfers() { LOTTERY.clear(); }

    public static void expireMonthTransfers() { MONTHS.expire(); }

    public static void clearMonthTransfers() { MONTHS.clear(); }

    public static void handleSynPlayerData(PlayerDataSyncPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            boolean initialSync = !SakuraClientState.isEnabled();
            try {
                IPlayerSignInData clientData = packet.getData();
                // 摘要刷新不能清除用户已按需加载的其他月份。
                clientData.setSignInRecords(new ArrayList<>(
                        SakuraPlayerData.get(player).getSignInRecords()
                ));
                SakuraPlayerData.setClient(player.getUUID(), clientData);
                if (initialSync) {
                    SakuraNetwork.sendToServer(new ClientConfigSyncPacket());
                }
            } catch (Exception exception) {
                LOGGER.warn("Unable to apply synchronized Sakura player data", exception);
            }
            SakuraClientState.setEnabled(true);
            refreshOpenSignInScreen();
        }
    }

    public static void handleAdvancement(AdvancementPacket packet) {
        SakuraClientState.setAdvancementData(packet.getAdvancements());
    }

    public static void handleMonthData(PlayerMonthSyncPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getUUID().equals(packet.getPlayerUUID())) {
            return;
        }
        try {
            Optional<List<String>> snapshot = MONTHS.accept(packet.getPart());
            if (!snapshot.isPresent()) return;
            List<SignInRecord> records = PlayerMonthSyncPacket.decodeRecords(packet.getMonth(), snapshot.get());
            IPlayerSignInData data = SakuraPlayerData.get(player);
            List<SignInRecord> merged = new ArrayList<>(data.getSignInRecords());
            merged.removeIf(record -> packet.getMonth().equals(PlayerMonthSyncPacket.monthOf(record)));
            merged.addAll(records);
            data.setSignInRecords(merged);
            refreshOpenSignInScreen();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to apply synchronized Sakura month {}", packet.getMonth(), exception);
        }
    }

    private static void refreshOpenSignInScreen() {
        if (Minecraft.getInstance().screen instanceof SignInScreen) {
            ((SignInScreen) Minecraft.getInstance().screen).refreshPlayerData();
        }
    }

    public static void handleRewardOptionSync(RewardOptionSyncPacket packet) {
        try {
            RewardConfigManager.updateRedactedRules(packet);
            RewardConfigManager.backupRewardOption();
            xin.vanilla.sakura.config.reward.RewardConfig candidate =
                    RewardConfigManager.fromSyncPacketList(Collections.singletonList(packet));
            candidate.setPersonalDatePresets(PersonalDatePresets.copy(
                    RewardConfigManager.getRewardConfig().getPersonalDatePresets()));
            candidate.setLotteryPools(LotteryPools.copy(
                    RewardConfigManager.getRewardConfig().getLotteryPools()));
            RewardConfigManager.setRewardConfig(candidate);
            RewardConfigManager.setRewardOptionDataChanged(true);
            RewardConfigManager.saveRewardOption();
            SakuraClientNotifications.success(
                    SakuraComponent.get().trans("word", "reward_option_download_success"),
                    SakuraNotificationTypes.REWARD
            );
        } catch (RuntimeException exception) {
            SakuraClientNotifications.error(
                    SakuraComponent.get().trans("word", "reward_option_download_failed"),
                    SakuraNotificationTypes.REWARD
            );
            throw exception;
        }
    }

    public static void handleRewardOptionUploadResult(boolean success) {
        Component message = SakuraComponent.get().trans("word", success ? "reward_option_upload_success" : "reward_option_upload_failed"
        );
        if (success) {
            SakuraClientNotifications.success(message, SakuraNotificationTypes.REWARD);
        } else {
            SakuraClientNotifications.error(message, SakuraNotificationTypes.REWARD);
        }
    }

    public static void handleCommonConfigSnapshot(CommonConfigSnapshotPacket packet) {
        packet.applyToClient();
        SakuraClientNotifications.success(
                SakuraComponent.get().transClient("word", "common_config_sync_success"),
                SakuraNotificationTypes.CONFIG
        );
    }

    public static void handlePersonalDatePresetSync(PersonalDatePresetSyncPacket packet) {
        java.util.Map<String, String> calendarNames = new java.util.LinkedHashMap<>();
        packet.getCalendars().forEach(calendar -> calendarNames.put(
                calendar.getId(), calendar.getDisplayNameKey()));
        SakuraClientState.setCalendarNames(calendarNames);
        RewardConfigManager.getRewardConfig().setPersonalDatePresets(
                PersonalDatePresets.copy(packet.getPresets()));
        RewardConfigManager.setRewardOptionDataChanged(true);
        RewardConfigManager.saveRewardOption();
    }

    public static void handleLotteryPoolSync(LotteryPoolSyncPacket packet, Object origin) {
        if (!currentConnection(origin) || packet.getGeneration() < SakuraClientState.getLotteryRequests().getGeneration()) return;
        LotteryPoolSyncPacket complete;
        try { complete = LOTTERY.accept(origin, packet); }
        catch (IllegalArgumentException failure) {
            LOTTERY.remove(origin); LOGGER.warn("Rejected lottery snapshot", failure); return;
        }
        if (complete == null || !SakuraClientState.getLotteryRequests().observeGeneration(complete.getGeneration())) return;
        RewardConfigManager.getRewardConfig().setLotteryPools(
                complete.getPools());
        RewardConfigManager.setRewardOptionDataChanged(true);
        RewardConfigManager.saveRewardOption();
    }

    public static void handleLotteryQuote(xin.vanilla.sakura.network.packet.LotteryQuotePacket packet, Object origin) {
        if (currentConnection(origin)) SakuraClientState.getLotteryRequests().accept(packet.getQuote());
    }

    public static void handleLotteryReveal(LotteryRevealPacket packet, Object origin) {
        if (!currentConnection(origin)
                || !SakuraClientState.getLotteryRequests().acceptReveal(packet.getOperationId())) return;
        SakuraClientState.getLotteryRequests().finishDraw(packet.getOperationId());
        Minecraft.getInstance().setScreen(new LotteryRevealScreen(
                Minecraft.getInstance().screen, packet));
    }

    public static void handleLotteryOutcome(xin.vanilla.sakura.network.packet.LotteryDrawOutcomePacket packet, Object origin) {
        if (currentConnection(origin)) SakuraClientState.getLotteryRequests().finishDraw(packet.getOperationId());
    }

    private static boolean currentConnection(Object origin) {
        return origin != null && Minecraft.getInstance().getConnection() != null
                && Minecraft.getInstance().getConnection().getConnection() == origin;
    }
}
