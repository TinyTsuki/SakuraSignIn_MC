package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.DateUtils;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class PlayerDataSyncPacket extends SplitPacket implements CustomPacketPayload {
    public final static Type<PlayerDataSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, "player_data_sync"));
    public final static StreamCodec<ByteBuf, PlayerDataSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull PlayerDataSyncPacket decode(@NotNull ByteBuf byteBuf) {
            return new PlayerDataSyncPacket((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull PlayerDataSyncPacket packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    private final UUID playerUUID;
    private final int totalSignInDays;
    private final int continuousSignInDays;
    private final Date lastSignInTime;
    private final int signInCard;
    private final boolean autoRewarded;
    private final List<SignInRecord> signInRecords;

    public PlayerDataSyncPacket(UUID playerUUID, PlayerSignInData data) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = data.getTotalSignInDays();
        this.continuousSignInDays = data.getContinuousSignInDays();
        this.lastSignInTime = data.getLastSignInTime();
        this.signInCard = data.getSignInCard();
        this.autoRewarded = data.isAutoRewarded();
        this.signInRecords = data.getSignInRecords();
    }

    public PlayerDataSyncPacket(FriendlyByteBuf buffer) {
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

    public PlayerDataSyncPacket(List<PlayerDataSyncPacket> packets) {
        super();
        this.playerUUID = packets.getFirst().playerUUID;
        this.totalSignInDays = packets.getFirst().totalSignInDays;
        this.continuousSignInDays = packets.getFirst().continuousSignInDays;
        this.lastSignInTime = packets.getFirst().lastSignInTime;
        this.signInCard = packets.getFirst().signInCard;
        this.autoRewarded = packets.getFirst().autoRewarded;
        this.signInRecords = packets.stream()
                .map(PlayerDataSyncPacket::getSignInRecords)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(SignInRecord::getSignInTime))
                .collect(Collectors.toList());
    }

    private PlayerDataSyncPacket(UUID playerUUID, int totalSignInDays, int continuousSignInDays, Date lastSignInTime, int signInCard, boolean autoRewarded) {
        super();
        this.playerUUID = playerUUID;
        this.totalSignInDays = totalSignInDays;
        this.continuousSignInDays = continuousSignInDays;
        this.lastSignInTime = lastSignInTime;
        this.signInCard = signInCard;
        this.autoRewarded = autoRewarded;
        this.signInRecords = new ArrayList<>();
    }

    public void toBytes(@NonNull FriendlyByteBuf buffer) {
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

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerDataSyncPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            ctx.enqueueWork(() -> {
                // 在客户端更新 PlayerSignInDataCapability
                // 获取玩家并更新 Capability 数据
                List<PlayerDataSyncPacket> packets = SplitPacket.handle(packet);
                if (CollectionUtils.isNotNullOrEmpty(packets)) {
                    ClientProxy.handleSynPlayerData(new PlayerDataSyncPacket(packets));
                }
            });
        }
    }

    @Override
    public int getChunkSize() {
        return ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get();
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<PlayerDataSyncPacket> split() {
        List<PlayerDataSyncPacket> result = new ArrayList<>();
        for (int i = 0, index = 0; i < signInRecords.size() / getChunkSize() + 1; i++) {
            PlayerDataSyncPacket packet = new PlayerDataSyncPacket(this.playerUUID, this.totalSignInDays, this.continuousSignInDays, this.lastSignInTime, this.signInCard, this.autoRewarded);
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
            PlayerDataSyncPacket packet = new PlayerDataSyncPacket(this.playerUUID, this.totalSignInDays, this.continuousSignInDays, this.lastSignInTime, this.signInCard, this.autoRewarded);
            packet.setSort(0);
            packet.setId(this.getId());
            packet.setTotal(1);
            result.add(packet);
        }
        return result;
    }

    public PlayerSignInData getData() {
        PlayerSignInData data = new PlayerSignInData();
        data.setTotalSignInDays(this.totalSignInDays);
        data.setContinuousSignInDays(this.continuousSignInDays);
        data.setLastSignInTime(this.lastSignInTime);
        data.setSignInCard(this.signInCard);
        data.setAutoRewarded(this.autoRewarded);
        data.setSignInRecords(this.signInRecords);
        return data;
    }
}
