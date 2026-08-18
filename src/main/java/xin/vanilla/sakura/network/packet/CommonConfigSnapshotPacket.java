package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.packet.ConfigSyncToServer;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sakura 自己的初始配置快照不会冒充 Banira 配置编辑器操作。 */
@Getter
public final class CommonConfigSnapshotPacket implements INetworkPacket {
    private static final int MAX_ENTRIES = 2048;
    private final Map<String, String> snapshot;

    public CommonConfigSnapshotPacket(Map<String, String> snapshot) {
        this.snapshot = snapshot == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(snapshot);
    }

    public CommonConfigSnapshotPacket(BaniraPacketBuffer buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid Sakura config snapshot size: " + size);
        }
        snapshot = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            snapshot.put(buffer.readUtf(256), buffer.readUtf(32767));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeVarInt(snapshot.size());
        snapshot.forEach((key, value) -> {
            buffer.writeUtf(key, 256);
            buffer.writeUtf(value == null ? "" : value, 32767);
        });
    }

    public static void handle(CommonConfigSnapshotPacket packet, BaniraNetworkContext context) {
        context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        context.markHandled();
    }

    public void applyToClient() {
        ConfigHolder holder = BaniraConfigs.holder(CommonConfig.class);
        if (holder == null) {
            return;
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        snapshot.forEach((path, value) -> {
            Object decoded = ConfigSyncToServer.decodeNetworkValue(holder, path, value);
            if (!holder.validate(path, decoded)) {
                throw new IllegalArgumentException("Invalid config value: " + path);
            }
            parsed.put(path, decoded);
        });
        parsed.forEach(holder::set);
        holder.save();
    }
}
