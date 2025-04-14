package xin.vanilla.sakura.network.packet;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static xin.vanilla.sakura.config.RewardConfigManager.GSON;

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
                    try {
                        // 备份 RewardOption
                        RewardConfigManager.backupRewardOption();
                        // 更新 RewardOption
                        RewardConfigManager.setRewardConfig(RewardConfigManager.fromSyncPacketList(packets));
                        RewardConfigManager.setRewardOptionDataChanged(true);
                        RewardConfigManager.saveRewardOption();
                    } catch (Exception e) {
                        Component component = Component.translatable(EI18nType.MESSAGE, "reward_option_download_failed");
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0x88FF5555));
                        throw e;
                    }
                    Component component = Component.translatable(EI18nType.MESSAGE, "reward_option_download_success");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component));
                } else if (ctx.get().getDirection().getReceptionSide().isServer()) {
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

                                // 同步 RewardOption 至所有在线玩家
                                for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
                                    if (player.getStringUUID().equals(sender.getStringUUID()))
                                        continue;
                                    // 仅给客户端已安装mod的玩家同步数据
                                    if (!SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString()))
                                        continue;
                                    for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardConfigManager.toSyncPacket(player).split()) {
                                        ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new RewardOptionDataReceivedNotice(false));
                            throw e;
                        }
                        ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new RewardOptionDataReceivedNotice(true));
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
        if (CollectionUtils.isNotNullOrEmpty(this.rewardOptionData)) {
            for (int i = 0, index = 0; i < this.rewardOptionData.size() / getChunkSize() + 1; i++) {
                RewardOptionSyncPacket packet = new RewardOptionSyncPacket(new ArrayList<>());
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
