package xin.vanilla.sakura.network.packet;

import xin.vanilla.sakura.config.CommonConfig;
import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.domain.player.MonthSignInIndex;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.DateUtils;

import java.util.*;

@Getter
public class PlayerDataSyncPacket extends SplitPacket {
    private final UUID playerUUID;
    private final int totalSignInDays;
    private final int continuousSignInDays;
    private final Date lastSignInTime;
    private final int signInCard;
    private final boolean autoRewarded;
    private final Map<String, MonthSignInIndex> monthIndexes;
    private final List<SignInRecord> signInRecords;

    public PlayerDataSyncPacket(UUID playerUUID, IPlayerSignInData data) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = data.getTotalSignInDays();
        this.continuousSignInDays = data.getContinuousSignInDays();
        this.lastSignInTime = data.getLastSignInTime();
        this.signInCard = data.getSignInCard();
        this.autoRewarded = data.isAutoRewarded();
        this.monthIndexes = copyIndexes(data.getMonthIndexes());
        this.signInRecords = data.getSignInRecords();
    }

    public PlayerDataSyncPacket(PacketBuffer buffer) {
        super(buffer);
        playerUUID = buffer.readUUID();
        this.totalSignInDays = buffer.readInt();
        this.continuousSignInDays = buffer.readInt();
        this.lastSignInTime = DateUtils.format(buffer.readUtf());
        this.signInCard = buffer.readInt();
        this.autoRewarded = buffer.readBoolean();
        this.monthIndexes = new LinkedHashMap<>();
        int monthIndexCount = buffer.readInt();
        for (int i = 0; i < monthIndexCount; i++) {
            MonthSignInIndex index =
                    MonthSignInIndex.deserializeNBT(Objects.requireNonNull(buffer.readNbt()));
            this.monthIndexes.put(index.getMonth(), index);
        }
        int size = buffer.readInt();
        this.signInRecords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.signInRecords.add(SignInRecord.readFromNBT(Objects.requireNonNull(buffer.readNbt())));
        }
    }

    public PlayerDataSyncPacket(List<PlayerDataSyncPacket> packets) {
        super();
        this.playerUUID = packets.get(0).playerUUID;
        this.totalSignInDays = packets.get(0).totalSignInDays;
        this.continuousSignInDays = packets.get(0).continuousSignInDays;
        this.lastSignInTime = packets.get(0).lastSignInTime;
        this.signInCard = packets.get(0).signInCard;
        this.autoRewarded = packets.get(0).autoRewarded;
        this.monthIndexes = copyIndexes(packets.get(0).monthIndexes);
        this.signInRecords = packets.stream()
                .map(PlayerDataSyncPacket::getSignInRecords)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(SignInRecord::getSignInTime))
                .toList();
    }

    private PlayerDataSyncPacket(
            UUID playerUUID,
            int totalSignInDays,
            int continuousSignInDays,
            Date lastSignInTime,
            int signInCard,
            boolean autoRewarded,
            Map<String, MonthSignInIndex> monthIndexes
    ) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = totalSignInDays;
        this.continuousSignInDays = continuousSignInDays;
        this.lastSignInTime = lastSignInTime;
        this.signInCard = signInCard;
        this.autoRewarded = autoRewarded;
        this.monthIndexes = copyIndexes(monthIndexes);
        this.signInRecords = new ArrayList<>();
    }

    public void toBytes(PacketBuffer buffer) {
        super.toBytes(buffer);
        buffer.writeUUID(playerUUID);
        buffer.writeInt(this.totalSignInDays);
        buffer.writeInt(this.continuousSignInDays);
        buffer.writeUtf(DateUtils.toDateTimeString(this.lastSignInTime));
        buffer.writeInt(this.signInCard);
        buffer.writeBoolean(this.autoRewarded);
        buffer.writeInt(this.monthIndexes.size());
        for (MonthSignInIndex index : this.monthIndexes.values()) {
            buffer.writeNbt(index.serializeNBT());
        }
        buffer.writeInt(this.signInRecords.size());
        for (SignInRecord record : this.signInRecords) {
            buffer.writeNbt(record.writeToNBT());
        }
    }

    public static void handle(PlayerDataSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                // 客户端只更新网络同步副本，不参与服务端持久化。
                List<PlayerDataSyncPacket> packets = SplitPacket.handle(packet);
                if (CollectionUtils.isNotNullOrEmpty(packets)) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProxy.handleSynPlayerData(new PlayerDataSyncPacket(packets)));
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    @Override
    public int getChunkSize() {
        return CommonConfig.get().server().playerDataSyncPacketSize();
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<PlayerDataSyncPacket> split() {
        List<PlayerDataSyncPacket> result = new ArrayList<>();
        for (int i = 0, index = 0; i < signInRecords.size() / getChunkSize() + 1; i++) {
            PlayerDataSyncPacket packet = new PlayerDataSyncPacket(
                    this.playerUUID,
                    this.totalSignInDays,
                    this.continuousSignInDays,
                    this.lastSignInTime,
                    this.signInCard,
                    this.autoRewarded,
                    this.monthIndexes
            );
            for (int j = 0; j < getChunkSize(); j++) {
                if (index >= signInRecords.size()) break;
                packet.signInRecords.add(this.signInRecords.get(index));
                index++;
            }
            packet.setId(this.getId());
            packet.setSort(i);
            result.add(packet);
        }
        result.forEach(packet -> packet.setTotal(result.size()));
        if (result.isEmpty()) {
            PlayerDataSyncPacket packet = new PlayerDataSyncPacket(
                    this.playerUUID,
                    this.totalSignInDays,
                    this.continuousSignInDays,
                    this.lastSignInTime,
                    this.signInCard,
                    this.autoRewarded,
                    this.monthIndexes
            );
            packet.setSort(0);
            packet.setId(this.getId());
            packet.setTotal(1);
            result.add(packet);
        }
        return result;
    }

    public IPlayerSignInData getData() {
        IPlayerSignInData data = new PlayerSignInData();
        data.setTotalSignInDays(this.totalSignInDays);
        data.setContinuousSignInDays(this.continuousSignInDays);
        data.setLastSignInTime(this.lastSignInTime);
        data.setSignInCard(this.signInCard);
        data.setAutoRewarded(this.autoRewarded);
        data.setMonthIndexes(this.monthIndexes);
        data.setSignInRecords(this.signInRecords);
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
