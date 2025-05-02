package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@Getter
public class AdvancementPacket extends SplitPacket implements CustomPacketPayload {
    public final static Type<AdvancementPacket> TYPE = new Type<>(new ResourceLocation(SakuraSignIn.MODID, "advancement"));
    public final static StreamCodec<ByteBuf, AdvancementPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull AdvancementPacket decode(@NotNull ByteBuf byteBuf) {
            return new AdvancementPacket((RegistryFriendlyByteBuf) byteBuf);
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull AdvancementPacket packet) {
            packet.toBytes((RegistryFriendlyByteBuf) byteBuf);
        }
    };

    // 存储要传输的AdvancementData对象
    private final List<AdvancementData> advancements;

    public AdvancementPacket(Collection<AdvancementHolder> advancements) {
        super();
        this.advancements = advancements.stream()
                .map(AdvancementData::fromAdvancement)
                .collect(Collectors.toList());
    }

    public AdvancementPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
        int size = buf.readVarInt();
        List<AdvancementData> advancements = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            advancements.add(AdvancementData.readFromBuffer(buf));
        }
        this.advancements = advancements;
    }

    private AdvancementPacket(List<AdvancementPacket> packets) {
        super();
        this.advancements = new ArrayList<>();
        this.advancements.addAll(packets.stream().flatMap(packet -> packet.getAdvancements().stream()).toList());
    }

    public void toBytes(@NotNull RegistryFriendlyByteBuf buf) {
        super.toBytes(buf);
        buf.writeVarInt(this.advancements.size());
        for (AdvancementData data : this.advancements) {
            data.writeToBuffer(buf);
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdvancementPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.enqueueWork(() -> {
                // 在客户端更新 List<AdvancementData>
                List<AdvancementPacket> packets = SplitPacket.handle(packet);
                if (CollectionUtils.isNotNullOrEmpty(packets)) {
                    ClientProxy.handleAdvancement(new AdvancementPacket(packets));
                }
            });
        }
    }

    @Override
    public int getChunkSize() {
        return 1024;
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<AdvancementPacket> split() {
        List<AdvancementPacket> result = new ArrayList<>();
        for (int i = 0, index = 0; i < advancements.size() / getChunkSize() + 1; i++) {
            AdvancementPacket packet = new AdvancementPacket(new ArrayList<AdvancementHolder>());
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

}
