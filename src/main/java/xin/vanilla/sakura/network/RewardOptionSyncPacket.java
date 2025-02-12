package xin.vanilla.sakura.network;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import xin.vanilla.sakura.config.RewardOptionDataManager;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static xin.vanilla.sakura.config.RewardOptionDataManager.GSON;

@Getter
public class RewardOptionSyncPacket extends SplitPacket {
    private final List<RewardOptionSyncData> rewardOptionData;

    public RewardOptionSyncPacket(List<RewardOptionSyncData> rewardOptionData) {
        super();
        this.rewardOptionData = rewardOptionData;
    }

    public RewardOptionSyncPacket(PacketBuffer buf) {
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
                    ServerPlayerEntity sender = ctx.get().getSender();
                    if (sender != null) {
                        // 判断是否为管理员
                        if (sender.hasPermissions(3)) {
                            // 备份 RewardOption
                            RewardOptionDataManager.backupRewardOption(false);
                            // 更新 RewardOption
                            RewardOptionDataManager.setRewardOptionData(RewardOptionDataManager.fromSyncPacketList(packets));
                            RewardOptionDataManager.saveRewardOption();

                            // TODO 返回同步成功与否回馈包
                            // 同步 RewardOption 至所有在线玩家
                            for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(true).split()) {
                                sender.server.getPlayerList().getPlayers().stream()
                                        // 排除发送者
                                        .filter(player -> !player.getStringUUID().equalsIgnoreCase(sender.getStringUUID()))
                                        .filter(player -> player.hasPermissions(3))
                                        .forEach(player -> ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket));
                            }
                            for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(false).split()) {
                                sender.server.getPlayerList().getPlayers().stream()
                                        // 排除发送者
                                        .filter(player -> !player.getStringUUID().equalsIgnoreCase(sender.getStringUUID()))
                                        .filter(player -> !player.hasPermissions(3))
                                        .forEach(player -> ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket));
                            }
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
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

    public void toBytes(PacketBuffer buf) {
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
}
