package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.advancements.AdvancementHolder;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.data.AdvancementData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通过 Banira 分包同步服务端进度定义。
 */
@Getter
public class AdvancementPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<AdvancementPacket>,
        SplitPacket.SplittableSplitPacket<AdvancementPacket> {
    private static final int CHUNK_SIZE = 1024;

    private final List<AdvancementData> advancements;

    public AdvancementPacket(Collection<AdvancementHolder> advancements) {
        this.advancements = advancements.stream()
                .map(AdvancementData::fromAdvancement)
                .collect(Collectors.toList());
    }

    private AdvancementPacket(List<AdvancementData> advancements, boolean copy) {
        this.advancements = copy ? new ArrayList<>(advancements) : advancements;
    }

    public AdvancementPacket(BaniraPacketBuffer buf) {
        super(buf);
        int size = buf.readVarInt();
        this.advancements = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            advancements.add(AdvancementData.readFromBuffer(buf));
        }
    }

    public void toBytes(BaniraPacketBuffer buf) {
        super.toBytes(buf);
        buf.writeVarInt(advancements.size());
        advancements.forEach(data -> data.writeToBuffer(buf));
    }

    public static void handle(AdvancementPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        ctx.markHandled();
    }

    @Override
    public List<AdvancementPacket> splitPacket() {
        List<AdvancementPacket> parts = new ArrayList<>();
        if (advancements.isEmpty()) {
            parts.add(new AdvancementPacket(Collections.emptyList(), true));
        } else {
            for (int start = 0; start < advancements.size(); start += CHUNK_SIZE) {
                int end = Math.min(start + CHUNK_SIZE, advancements.size());
                parts.add(new AdvancementPacket(advancements.subList(start, end), true));
            }
        }
        initializeParts(parts);
        return parts;
    }

    @Override
    public AdvancementPacket mergePackets(List<AdvancementPacket> packets) {
        List<AdvancementData> merged = new ArrayList<>();
        packets.forEach(packet -> merged.addAll(packet.advancements));
        return new AdvancementPacket(merged, false);
    }

    @Override
    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    private void initializeParts(List<AdvancementPacket> packets) {
        for (int i = 0; i < packets.size(); i++) {
            AdvancementPacket packet = packets.get(i);
            packet.setId(getId());
            packet.setSort(i);
            packet.setTotal(packets.size());
        }
    }
}
