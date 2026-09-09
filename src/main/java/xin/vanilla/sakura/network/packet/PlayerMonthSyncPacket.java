package xin.vanilla.sakura.network.packet;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import net.minecraft.nbt.TagParser;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.month.MonthDataTransfer;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 单月签到详情；服务端全历史不会进入网络负载。
 */
@Getter
public class PlayerMonthSyncPacket implements INetworkPacket {
    private static final AtomicLong REVISION = new AtomicLong();
    private final MonthDataTransfer.Part part;

    private PlayerMonthSyncPacket(MonthDataTransfer.Part part) {
        this.part = part;
    }

    public static List<PlayerMonthSyncPacket> prepare(UUID playerUUID, String month, List<SignInRecord> records) {
        List<SignInRecord> snapshot = records.stream()
                .filter(record -> month.equals(monthOf(record)))
                .sorted(Comparator.comparing(SignInRecord::getCompensateTime))
                .collect(Collectors.toList());
        if (snapshot.size() > MonthDataTransfer.MAX_RECORDS) {
            throw new IllegalArgumentException("Too many sign-in month records");
        }
        List<String> serialized = snapshot.stream().map(record -> record.writeToNBT().toString())
                .collect(Collectors.toList());
        return MonthDataTransfer.encode(playerUUID, month, REVISION.incrementAndGet(), serialized)
                .stream().map(PlayerMonthSyncPacket::new).collect(Collectors.toList());
    }

    public PlayerMonthSyncPacket(BaniraPacketBuffer buffer) {
        part = new MonthDataTransfer.Part(buffer.readUuid(), buffer.readUtf(7), buffer.readLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(MonthDataTransfer.MAX_PART_CHARS));
    }

    public UUID getPlayerUUID() { return part.getPlayer(); }

    public String getMonth() { return part.getMonth(); }

    public static List<SignInRecord> decodeRecords(String month, List<String> serialized) {
        List<SignInRecord> records = new ArrayList<>(serialized.size());
        try {
            for (String value : serialized) {
                net.minecraft.nbt.CompoundTag tag = TagParser.parseTag(value);
                SignInRecord record = SignInRecord.readFromNBT(tag);
                if (!month.equals(monthOf(record)) || !tag.equals(record.writeToNBT())) {
                    throw new IllegalArgumentException("Inconsistent sign-in month record");
                }
                records.add(record);
            }
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid sign-in month payload", exception);
        }
        return records;
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUuid(part.getPlayer());
        buffer.writeUtf(part.getMonth(), 7);
        buffer.writeLong(part.getRevision());
        buffer.writeVarInt(part.getIndex());
        buffer.writeVarInt(part.getCount());
        buffer.writeVarInt(part.getDecodedBytes());
        buffer.writeUtf(part.getPayload(), MonthDataTransfer.MAX_PART_CHARS);
    }

    public static void handle(PlayerMonthSyncPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        ctx.markHandled();
    }

    public static String monthOf(SignInRecord record) {
        return YearMonth.from(record.getCompensateTime().toInstant()
                .atZone(ZoneId.systemDefault())).toString();
    }

}
