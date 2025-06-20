package xin.vanilla.sakura.network.packet;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRewardRule;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.SakuraUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static xin.vanilla.sakura.rewards.RewardConfigManager.GSON;

@Getter
public class RewardOptionSyncToBoth extends SplitPacket {
    private final List<RewardOptionSyncData> rewardOptionData;

    public RewardOptionSyncToBoth(List<RewardOptionSyncData> rewardOptionData) {
        super();
        this.rewardOptionData = rewardOptionData;
    }

    public RewardOptionSyncToBoth(PacketBuffer buf) {
        super(buf);
        this.rewardOptionData = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            this.rewardOptionData.add(new RewardOptionSyncData(
                    EnumRewardRule.valueOf(buf.readInt()),
                    buf.readUtf(),
                    GSON.fromJson(new String(buf.readByteArray(), StandardCharsets.UTF_8), new TypeToken<Reward>() {
                    }.getType())
            ));
        }
    }

    public static void handle(RewardOptionSyncToBoth packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            List<RewardOptionSyncToBoth> packets = SplitPacket.handle(packet);
            if (CollectionUtils.isNotNullOrEmpty(packets)) {
                // 客户端
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    try {
                        // 备份 RewardOption
                        RewardConfigManager.backupRewardOption();
                        // 更新 RewardOption
                        RewardConfigManager.setRewardConfig(RewardConfigManager.fromSyncPacketList(packets));
                        RewardConfigManager.setRewardOptionDataChanged(true);
                        RewardConfigManager.saveRewardOption();
                    } catch (Exception e) {
                        Component component = Component.translatable(EnumI18nType.MESSAGE, "reward_option_download_failed");
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FFFF55));
                        throw e;
                    }
                    Component component = Component.translatable(EnumI18nType.MESSAGE, "reward_option_download_success");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component));
                }
                // 服务端
                else if (ctx.get().getDirection().getReceptionSide().isServer()) {
                    ServerPlayerEntity sender = ctx.get().getSender();
                    if (sender != null) {
                        try {
                            // 判断是否拥有修改权限
                            if (sender.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                                // 备份 RewardOption
                                RewardConfigManager.backupRewardOption(false);
                                // 更新 RewardOption
                                RewardConfigManager.setRewardConfig(RewardConfigManager.fromSyncPacketList(packets));
                                RewardConfigManager.saveRewardOption();
                            }
                        } catch (Exception e) {
                            SakuraUtils.sendPacketToPlayer(new RewardOptionReceivedToClient(false), sender);
                            throw e;
                        }
                        SakuraUtils.sendPacketToPlayer(new RewardOptionReceivedToClient(true), sender);
                    }

                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 将数据包拆分为多个小包
     */
    public List<RewardOptionSyncToBoth> split() {
        List<RewardOptionSyncToBoth> result = new ArrayList<>();
        if (CollectionUtils.isNotNullOrEmpty(this.rewardOptionData)) {
            for (int i = 0, index = 0; i < this.rewardOptionData.size() / getChunkSize() + 1; i++) {
                RewardOptionSyncToBoth packet = new RewardOptionSyncToBoth(new ArrayList<>());
                for (int j = 0; j < getChunkSize(); j++) {
                    if (index >= this.rewardOptionData.size()) break;
                    packet.rewardOptionData.add(this.rewardOptionData.get(index));
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
