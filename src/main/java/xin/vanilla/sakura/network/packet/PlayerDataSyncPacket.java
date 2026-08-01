package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.personaldate.PersonalDateCalendar;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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
    private final List<PlayerPersonalDateSlot> personalDateSlots;

    public PlayerDataSyncPacket(UUID playerUUID, IPlayerSignInData data) {
        this.playerUUID = playerUUID;
        this.totalSignInDays = data.getTotalSignInDays();
        this.continuousSignInDays = data.getContinuousSignInDays();
        this.lastSignInTime = data.getLastSignInTime();
        this.signInCard = data.getSignInCard();
        this.autoRewarded = data.isAutoRewarded();
        this.monthIndexes = copyIndexes(data.getMonthIndexes());
        this.personalDateSlots = copySlots(data.getPersonalDateSlots());
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
        personalDateSlots = new ArrayList<>();
        int slotCount = buffer.readVarInt();
        for (int i = 0; i < slotCount; i++) {
            personalDateSlots.add(new PlayerPersonalDateSlot(
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readEnum(PersonalDateCalendar.class),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf()
            ));
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
        buffer.writeVarInt(personalDateSlots.size());
        for (PlayerPersonalDateSlot slot : personalDateSlots) {
            buffer.writeUtf(slot.getPresetId());
            buffer.writeVarInt(slot.getSlotIndex());
            buffer.writeEnum(slot.getCalendar());
            buffer.writeVarInt(slot.getMonth());
            buffer.writeVarInt(slot.getDay());
            buffer.writeUtf(slot.getLastClaimedOccurrenceKey());
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
        data.setPersonalDateSlots(personalDateSlots);
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

    private static List<PlayerPersonalDateSlot> copySlots(List<PlayerPersonalDateSlot> source) {
        List<PlayerPersonalDateSlot> copy = new ArrayList<>();
        source.forEach(slot -> copy.add(
                PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT())));
        return copy;
    }
}
