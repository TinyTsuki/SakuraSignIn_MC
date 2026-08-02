package xin.vanilla.sakura.network;

import xin.vanilla.sakura.network.packet.AdvancementPacket;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.network.packet.PersonalDatePresetSyncPacket;
import xin.vanilla.sakura.network.packet.CommonConfigSnapshotPacket;
import xin.vanilla.sakura.network.packet.LotteryPoolSyncPacket;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;

import java.util.function.Consumer;

/**
 * 公共包处理器通过回调进入客户端代码，专用服务器不会链接 Minecraft 客户端类。
 */
public final class SakuraClientPacketHandlers {
    private static Consumer<PlayerDataSyncPacket> playerSummary = packet -> {
    };
    private static Consumer<PlayerMonthSyncPacket> playerMonth = packet -> {
    };
    private static Consumer<AdvancementPacket> advancements = packet -> {
    };
    private static Consumer<RewardOptionSyncPacket> rewardOptions = packet -> {
    };
    private static Consumer<PersonalDatePresetSyncPacket> personalDatePresets = packet -> {
    };
    private static Consumer<CommonConfigSnapshotPacket> commonConfig = packet -> {
    };
    private static Consumer<LotteryPoolSyncPacket> lotteryPools = packet -> {
    };
    private static Consumer<LotteryRevealPacket> lotteryReveal = packet -> {
    };
    private static Consumer<Boolean> rewardUploadResult = success -> {
    };

    private SakuraClientPacketHandlers() {
    }

    public static void register(Consumer<PlayerDataSyncPacket> playerSummaryHandler,
                                Consumer<PlayerMonthSyncPacket> playerMonthHandler,
                                Consumer<AdvancementPacket> advancementHandler,
                                Consumer<RewardOptionSyncPacket> rewardOptionsHandler,
                                Consumer<PersonalDatePresetSyncPacket> personalDatePresetsHandler,
                                Consumer<CommonConfigSnapshotPacket> commonConfigHandler,
                                Consumer<LotteryPoolSyncPacket> lotteryPoolsHandler,
                                Consumer<LotteryRevealPacket> lotteryRevealHandler,
                                Consumer<Boolean> rewardUploadResultHandler) {
        playerSummary = playerSummaryHandler;
        playerMonth = playerMonthHandler;
        advancements = advancementHandler;
        rewardOptions = rewardOptionsHandler;
        personalDatePresets = personalDatePresetsHandler;
        commonConfig = commonConfigHandler;
        lotteryPools = lotteryPoolsHandler;
        lotteryReveal = lotteryRevealHandler;
        rewardUploadResult = rewardUploadResultHandler;
    }

    public static void handle(PlayerDataSyncPacket packet) {
        playerSummary.accept(packet);
    }

    public static void handle(PlayerMonthSyncPacket packet) {
        playerMonth.accept(packet);
    }

    public static void handle(AdvancementPacket packet) {
        advancements.accept(packet);
    }

    public static void handle(RewardOptionSyncPacket packet) {
        rewardOptions.accept(packet);
    }

    public static void handle(PersonalDatePresetSyncPacket packet) {
        personalDatePresets.accept(packet);
    }

    public static void handle(CommonConfigSnapshotPacket packet) {
        commonConfig.accept(packet);
    }

    public static void handle(LotteryPoolSyncPacket packet) {
        lotteryPools.accept(packet);
    }

    public static void handle(LotteryRevealPacket packet) {
        lotteryReveal.accept(packet);
    }

    public static void handleRewardUploadResult(boolean success) {
        rewardUploadResult.accept(success);
    }
}
