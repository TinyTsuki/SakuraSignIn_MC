package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.domain.player.MonthSignInIndex;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家永久摘要，不携带可按月清理的签到详情。
 */
@Getter
public class PlayerDataSyncPacket implements INetworkPacket {
    private final UUID playerUUID;
    private final int totalSignInDays;
    private final int continuousSignInDays;
    private final Date lastSignInTime;
    private final int signInCard;
    private final boolean autoRewarded;
    private final Map<String, MonthSignInIndex> monthIndexes;

    public PlayerDataSyncPacket(UUID playerUUID, IPlayerSignInData data) {
        this.playerUUID = playerUUID;
        this.totalSignInDays = data.getTotalSignInDays();
        this.continuousSignInDays = data.getContinuousSignInDays();
        this.lastSignInTime = data.getLastSignInTime();
        this.signInCard = data.getSignInCard();
        this.autoRewarded = data.isAutoRewarded();
        this.monthIndexes = copyIndexes(data.getMonthIndexes());
    }

    public PlayerDataSyncPacket(BaniraPacketBuffer buffer) {
        playerUUID = buffer.readUuid();
        totalSignInDays = buffer.readInt();
        continuousSignInDays = buffer.readInt();
        lastSignInTime = DateUtils.format(buffer.readUtf());
        signInCard = buffer.readInt();
        autoRewarded = buffer.readBoolean();
        monthIndexes = new LinkedHashMap<>();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            MonthSignInIndex index = new MonthSignInIndex(buffer.readUtf());
            index.setSignedDays(buffer.readInt());
            index.setRewardedDays(buffer.readInt());
            monthIndexes.put(index.getMonth(), index);
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUuid(playerUUID);
        buffer.writeInt(totalSignInDays);
        buffer.writeInt(continuousSignInDays);
        buffer.writeUtf(DateUtils.toDateTimeString(lastSignInTime));
        buffer.writeInt(signInCard);
        buffer.writeBoolean(autoRewarded);
        buffer.writeVarInt(monthIndexes.size());
        for (MonthSignInIndex index : monthIndexes.values()) {
            buffer.writeUtf(index.getMonth());
            buffer.writeInt(index.getSignedDays());
            buffer.writeInt(index.getRewardedDays());
        }
    }

    public static void handle(PlayerDataSyncPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        ctx.markHandled();
    }

    public IPlayerSignInData getData() {
        IPlayerSignInData data = new PlayerSignInData();
        data.setTotalSignInDays(totalSignInDays);
        data.setContinuousSignInDays(continuousSignInDays);
        data.setLastSignInTime(lastSignInTime);
        data.setSignInCard(signInCard);
        data.setAutoRewarded(autoRewarded);
        data.setMonthIndexes(monthIndexes);
        return data;
    }

    private static Map<String, MonthSignInIndex> copyIndexes(
            Map<String, MonthSignInIndex> source
    ) {
        Map<String, MonthSignInIndex> copy = new LinkedHashMap<>();
        source.forEach((month, index) ->
                copy.put(month, MonthSignInIndex.deserializeNBT(index.serializeNBT())));
        return copy;
    }
}
