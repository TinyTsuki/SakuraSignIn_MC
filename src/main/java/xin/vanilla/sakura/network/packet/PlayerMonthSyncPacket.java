package xin.vanilla.sakura.network.packet;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import net.minecraft.nbt.JsonToNBT;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 单月签到详情；服务端全历史不会进入网络负载。
 */
@Getter
public class PlayerMonthSyncPacket implements INetworkPacket {
    private final UUID playerUUID;
    private final String month;
    private final List<SignInRecord> records;

    public PlayerMonthSyncPacket(UUID playerUUID, String month, List<SignInRecord> records) {
        this.playerUUID = playerUUID;
        this.month = month;
        this.records = records.stream()
                .filter(record -> month.equals(monthOf(record)))
                .sorted(Comparator.comparing(SignInRecord::getCompensateTime))
                .collect(Collectors.toList());
    }

    public PlayerMonthSyncPacket(BaniraPacketBuffer buffer) {
        playerUUID = buffer.readUuid();
        month = buffer.readUtf(7);
        int size = buffer.readVarInt();
        records = new ArrayList<>(size);
        try {
            for (int i = 0; i < size; i++) {
                records.add(SignInRecord.readFromNBT(JsonToNBT.parseTag(buffer.readUtf())));
            }
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid sign-in month payload", exception);
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUuid(playerUUID);
        buffer.writeUtf(month, 7);
        buffer.writeVarInt(records.size());
        records.forEach(record -> buffer.writeUtf(record.writeToNBT().toString()));
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
