package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInData;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.DateUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Getter
public class PlayerDataSyncToBoth extends SplitPacket {
    private final UUID playerUUID;
    private final int totalSignInDays;
    private final int continuousSignInDays;
    private final Date lastSignInTime;
    private final int signInCard;
    private final boolean autoRewarded;
    private final List<SignInRecord> signInRecords;

    public PlayerDataSyncToBoth(UUID playerUUID, IPlayerSignInData data) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = data.getTotalSignInDays();
        this.continuousSignInDays = data.getContinuousSignInDays();
        this.lastSignInTime = data.getLastSignInTime();
        this.signInCard = data.getSignInCard();
        this.autoRewarded = data.isAutoRewarded();
        this.signInRecords = data.getSignInRecords();
    }

    public PlayerDataSyncToBoth(PacketBuffer buffer) {
        super(buffer);
        playerUUID = buffer.readUUID();
        this.totalSignInDays = buffer.readInt();
        this.continuousSignInDays = buffer.readInt();
        this.lastSignInTime = DateUtils.format(buffer.readUtf());
        this.signInCard = buffer.readInt();
        this.autoRewarded = buffer.readBoolean();
        int size = buffer.readInt();
        this.signInRecords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.signInRecords.add(SignInRecord.readFromNBT(Objects.requireNonNull(buffer.readNbt())));
        }
    }

    public PlayerDataSyncToBoth(List<PlayerDataSyncToBoth> packets) {
        super();
        this.playerUUID = packets.get(0).playerUUID;
        this.totalSignInDays = packets.get(0).totalSignInDays;
        this.continuousSignInDays = packets.get(0).continuousSignInDays;
        this.lastSignInTime = packets.get(0).lastSignInTime;
        this.signInCard = packets.get(0).signInCard;
        this.autoRewarded = packets.get(0).autoRewarded;
        this.signInRecords = packets.stream()
                .map(PlayerDataSyncToBoth::getSignInRecords)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(SignInRecord::getSignInTime))
                .collect(Collectors.toList());
    }

    private PlayerDataSyncToBoth(UUID playerUUID, int totalSignInDays, int continuousSignInDays, Date lastSignInTime, int signInCard, boolean autoRewarded) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = totalSignInDays;
        this.continuousSignInDays = continuousSignInDays;
        this.lastSignInTime = lastSignInTime;
        this.signInCard = signInCard;
        this.autoRewarded = autoRewarded;
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
        buffer.writeInt(this.signInRecords.size());
        for (SignInRecord record : this.signInRecords) {
            buffer.writeNbt(record.writeToNBT());
        }
    }

    public static void handle(PlayerDataSyncToBoth packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                List<PlayerDataSyncToBoth> packets = SplitPacket.handle(packet);
                if (CollectionUtils.isNotNullOrEmpty(packets)) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProxy.handleSynPlayerData(new PlayerDataSyncToBoth(packets)));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @Override
    public int getChunkSize() {
        return ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get();
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<PlayerDataSyncToBoth> split() {
        List<PlayerDataSyncToBoth> result = new ArrayList<>();
        for (int i = 0, index = 0; i < signInRecords.size() / getChunkSize() + 1; i++) {
            PlayerDataSyncToBoth packet = new PlayerDataSyncToBoth(this.playerUUID, this.totalSignInDays, this.continuousSignInDays, this.lastSignInTime, this.signInCard, this.autoRewarded);
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
            PlayerDataSyncToBoth packet = new PlayerDataSyncToBoth(this.playerUUID, this.totalSignInDays, this.continuousSignInDays, this.lastSignInTime, this.signInCard, this.autoRewarded);
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
        data.setSignInRecords(this.signInRecords);
        return data;
    }
}
