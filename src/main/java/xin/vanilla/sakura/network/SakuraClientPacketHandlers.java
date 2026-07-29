package xin.vanilla.sakura.network;

import xin.vanilla.sakura.network.packet.AdvancementPacket;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;

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
    private static Consumer<Boolean> rewardUploadResult = success -> {
    };

    private SakuraClientPacketHandlers() {
    }

    public static void register(Consumer<PlayerDataSyncPacket> playerSummaryHandler,
                                Consumer<PlayerMonthSyncPacket> playerMonthHandler,
                                Consumer<AdvancementPacket> advancementHandler,
                                Consumer<RewardOptionSyncPacket> rewardOptionsHandler,
                                Consumer<Boolean> rewardUploadResultHandler) {
        playerSummary = playerSummaryHandler;
        playerMonth = playerMonthHandler;
        advancements = advancementHandler;
        rewardOptions = rewardOptionsHandler;
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

    public static void handleRewardUploadResult(boolean success) {
        rewardUploadResult.accept(success);
    }
}
