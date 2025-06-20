package xin.vanilla.sakura.network.packet;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.RewardCell;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.enums.EnumSignInStatus;
import xin.vanilla.sakura.network.data.RewardCellSyncData;
import xin.vanilla.sakura.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static xin.vanilla.sakura.rewards.RewardConfigManager.GSON;

@Getter
public class RewardCellSyncToClient extends SplitPacket {
    private final List<RewardCellSyncData> rewardCells;

    public RewardCellSyncToClient(List<RewardCellSyncData> rewardCells) {
        super();
        this.rewardCells = rewardCells.stream()
                .collect(Collectors.groupingBy(RewardCellSyncData::getDate))
                .values().stream()
                .peek(value -> {
                    List<RewardCellSyncData> dataList = value.stream()
                            .filter(data -> !EnumRewardType.NONE.equals(data.getReward().getType()))
                            .collect(Collectors.toList());
                    if (dataList.isEmpty()) dataList.add(value.get(0));
                    value.clear();
                    value.addAll(dataList);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public RewardCellSyncToClient(PacketBuffer buf) {
        super(buf);
        this.rewardCells = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            this.rewardCells.add(new RewardCellSyncData(
                    EnumSignInStatus.valueOf(buf.readInt()),
                    buf.readUtf(),
                    GSON.fromJson(new String(buf.readByteArray(), StandardCharsets.UTF_8), new TypeToken<Reward>() {
                    }.getType())
            ));
        }
    }

    public static void handle(RewardCellSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            List<RewardCellSyncToClient> packets = SplitPacket.handle(packet);
            if (CollectionUtils.isNotNullOrEmpty(packets)) {
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    Map<String, RewardCell> map = packets.stream()
                            .flatMap(p -> p.getRewardCells().stream())
                            .collect(Collectors.toMap(RewardCellSyncData::getDate
                                    , data ->
                                            new RewardCell(data.getStatus()
                                                    , data.getDate()
                                                    , EnumRewardType.NONE.equals(data.getReward().getType())
                                                    ? new RewardList()
                                                    : new RewardList(data.getReward())
                                            )
                                    , (oldValue, newValue) -> {
                                        oldValue.getRewards().addAll(newValue.getRewards());
                                        return oldValue;
                                    }
                            ));
                    SakuraSignIn.getRewardCellMap().putAll(map);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<RewardCellSyncToClient> split() {
        List<RewardCellSyncToClient> result = new ArrayList<>();
        if (CollectionUtils.isNotNullOrEmpty(this.rewardCells)) {
            for (int i = 0, index = 0; i < this.rewardCells.size() / getChunkSize() + 1; i++) {
                RewardCellSyncToClient packet = new RewardCellSyncToClient(new ArrayList<>());
                for (int j = 0; j < getChunkSize(); j++) {
                    if (index >= this.rewardCells.size()) break;
                    packet.rewardCells.add(this.rewardCells.get(index));
                    index++;
                }
                packet.setId(this.getId());
                packet.setSort(i);
                result.add(packet);
            }
            result.forEach(packet -> packet.setTotal(result.size()));
        }
        return result;
    }

    public void toBytes(PacketBuffer buf) {
        super.toBytes(buf);
        buf.writeInt(rewardCells.size());
        for (RewardCellSyncData data : rewardCells) {
            buf.writeInt(data.getStatus().getCode());
            buf.writeUtf(data.getDate());
            buf.writeByteArray(GSON.toJson(data.getReward().toJsonObject()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public int getChunkSize() {
        return 1024;
    }
}
