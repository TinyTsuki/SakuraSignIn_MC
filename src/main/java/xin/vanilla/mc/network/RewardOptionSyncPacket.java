package xin.vanilla.mc.network;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import xin.vanilla.mc.config.RewardOptionDataManager;
import xin.vanilla.mc.enums.ERewardRule;
import xin.vanilla.mc.rewards.Reward;
import xin.vanilla.mc.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static xin.vanilla.mc.config.RewardOptionDataManager.GSON;

@Getter
public class RewardOptionSyncPacket extends SplitPacket {
    private final List<RewardOptionSyncData> rewardOptionData;

    public RewardOptionSyncPacket(List<RewardOptionSyncData> rewardOptionData) {
        super();
        this.rewardOptionData = rewardOptionData;
    }

    public RewardOptionSyncPacket(FriendlyByteBuf buf) {
        super(buf);
        this.rewardOptionData = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            this.rewardOptionData.add(new RewardOptionSyncData(
                    ERewardRule.valueOf(buf.readInt()),
                    buf.readUtf(),
                    GSON.fromJson(new String(buf.readByteArray(), StandardCharsets.UTF_8), new TypeToken<Reward>() {
                    }.getType())
            ));
        }
    }

    public static void handle(RewardOptionSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            List<RewardOptionSyncPacket> packets = SplitPacket.handle(packet);
            if (CollectionUtils.isNotNullOrEmpty(packets)) {
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    // 备份 RewardOption
                    RewardOptionDataManager.backupRewardOption();
                    // 更新 RewardOption
                    RewardOptionDataManager.setRewardOptionData(RewardOptionDataManager.fromSyncPacketList(packets));
                    RewardOptionDataManager.setRewardOptionDataChanged(true);
                    RewardOptionDataManager.saveRewardOption();
                } else if (ctx.get().getDirection().getReceptionSide().isServer()) {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        // 判断是否为管理员
                        if (sender.hasPermissions(3)) {
                            // 备份 RewardOption
                            RewardOptionDataManager.backupRewardOption(false);
                            // 更新 RewardOption
                            RewardOptionDataManager.setRewardOptionData(RewardOptionDataManager.fromSyncPacketList(packets));
                            RewardOptionDataManager.saveRewardOption();

                            // 同步 RewardOption 至所有在线玩家
                            for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket().split()) {
                                for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
                                    // 排除发送者
                                    if (player.getStringUUID().equalsIgnoreCase(sender.getStringUUID())) continue;
                                    ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket);
                                }
                            }
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public void toBytes(FriendlyByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(rewardOptionData.size());
        for (RewardOptionSyncData data : rewardOptionData) {
            buf.writeInt(data.getRule().getCode());
            buf.writeUtf(data.getKey());
            buf.writeByteArray(GSON.toJson(data.getReward().toJsonObject()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public int getChunkSize() {
        return 1024;
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<RewardOptionSyncPacket> split() {
        List<RewardOptionSyncPacket> result = new ArrayList<>();
        for (int i = 0, index = 0; i < rewardOptionData.size() / getChunkSize() + 1; i++) {
            RewardOptionSyncPacket packet = new RewardOptionSyncPacket(new ArrayList<>());
            for (int j = 0; j < getChunkSize(); j++) {
                if (index >= rewardOptionData.size()) break;
                packet.rewardOptionData.add(this.rewardOptionData.get(index));
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
