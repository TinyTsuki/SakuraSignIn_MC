package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@Getter
public class AdvancementToClient extends SplitPacket {
    private final List<AdvancementData> advancements;

    public AdvancementToClient(Collection<Advancement> advancements) {
        super();
        this.advancements = advancements.stream()
                .map(AdvancementData::fromAdvancement)
                .collect(Collectors.toList());
    }

    public AdvancementToClient(PacketBuffer buf) {
        super(buf);
        int size = buf.readVarInt();
        List<AdvancementData> advancements = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            advancements.add(AdvancementData.readFromBuffer(buf));
        }
        this.advancements = advancements;
    }

    private AdvancementToClient(List<AdvancementToClient> packets) {
        super();
        this.advancements = new ArrayList<>();
        this.advancements.addAll(packets.stream().flatMap(packet -> packet.getAdvancements().stream()).collect(Collectors.toList()));
    }

    public static void handle(AdvancementToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                List<AdvancementToClient> packets = SplitPacket.handle(packet);
                if (CollectionUtils.isNotNullOrEmpty(packets)) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProxy.handleAdvancement(new AdvancementToClient(packets)));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @Override
    public int getChunkSize() {
        return 1024;
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<AdvancementToClient> split() {
        List<AdvancementToClient> result = new ArrayList<>();
        for (int i = 0, index = 0; i < advancements.size() / getChunkSize() + 1; i++) {
            AdvancementToClient packet = new AdvancementToClient(new ArrayList<Advancement>());
            for (int j = 0; j < getChunkSize(); j++) {
                if (index >= advancements.size()) break;
                packet.advancements.add(this.advancements.get(index));
                index++;
            }
            packet.setId(this.getId());
            packet.setSort(i);
            result.add(packet);
        }
        result.forEach(packet -> packet.setTotal(result.size()));
        return result;
    }

    public void toBytes(PacketBuffer buf) {
        super.toBytes(buf);
        buf.writeVarInt(this.advancements.size());
        for (AdvancementData data : this.advancements) {
            data.writeToBuffer(buf);
        }
    }

}
