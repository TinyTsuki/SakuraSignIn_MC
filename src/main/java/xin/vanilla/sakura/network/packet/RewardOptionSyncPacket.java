package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.network.data.RewardOptionSyncKind;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardAddPermissionChecker;
import xin.vanilla.sakura.reward.RewardJsonCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 有序奖励配置分包；组顺序和重复概率组均保持不变。
 */
@Getter
public class RewardOptionSyncPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<RewardOptionSyncPacket>,
        SplitPacket.SplittableSplitPacket<RewardOptionSyncPacket> {
    private static final int CHUNK_SIZE = 1024;

    private final List<RewardOptionSyncData> rewardOptionData;

    public RewardOptionSyncPacket(List<RewardOptionSyncData> rewardOptionData) {
        this.rewardOptionData = new ArrayList<>(rewardOptionData);
    }

    public RewardOptionSyncPacket(BaniraPacketBuffer buf) {
        super(buf);
        this.rewardOptionData = new ArrayList<>();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            RewardOptionSyncKind kind = buf.readEnum(RewardOptionSyncKind.class);
            ERewardRule rule = ERewardRule.valueOf(buf.readInt());
            String key = buf.readUtf();
            Reward reward = kind == RewardOptionSyncKind.REWARD
                    ? RewardJsonCodec.decode(new com.google.gson.JsonParser().parse(buf.readUtf()))
                    : null;
            rewardOptionData.add(new RewardOptionSyncData(kind, rule, key, reward));
        }
    }

    public void toBytes(BaniraPacketBuffer buf) {
        super.toBytes(buf);
        buf.writeVarInt(rewardOptionData.size());
        for (RewardOptionSyncData data : rewardOptionData) {
            buf.writeEnum(data.getKind());
            buf.writeInt(data.getRule().getCode());
            buf.writeUtf(data.getKey());
            if (data.getKind() == RewardOptionSyncKind.REWARD) {
                buf.writeUtf(RewardJsonCodec.encode(data.getReward()).toString());
            }
        }
    }

    public static void handle(RewardOptionSyncPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.isClientSide()) {
                SakuraClientPacketHandlers.handle(packet);
                return;
            }
            ServerPlayerEntity sender = ctx.senderAs(ServerPlayerEntity.class);
            if (sender == null) {
                return;
            }
            try {
                if (!sender.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
                    BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
                    return;
                }
                xin.vanilla.sakura.config.reward.RewardConfig candidate =
                        RewardConfigManager.fromSyncPacketList(Collections.singletonList(packet));
                if (!RewardAddPermissionChecker.canApply(sender,
                        RewardConfigManager.getRewardConfig(), candidate)) {
                    BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
                    return;
                }
                RewardConfigManager.backupRewardOption(false);
                RewardConfigManager.setRewardConfig(candidate);
                RewardConfigManager.saveRewardOption();
                for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
                    if (!player.getUUID().equals(sender.getUUID())) {
                        SakuraNetwork.sendSplitToPlayer(
                                RewardConfigManager.toSyncPacket(player), player
                        );
                    }
                }
                BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(true), sender);
            } catch (RuntimeException exception) {
                BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
                throw exception;
            }
        });
        ctx.markHandled();
    }

    @Override
    public List<RewardOptionSyncPacket> splitPacket() {
        if (rewardOptionData.isEmpty()) {
            return singletonEmptyPart();
        }
        List<RewardOptionSyncPacket> result = new ArrayList<>();
        for (int start = 0; start < rewardOptionData.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, rewardOptionData.size());
            result.add(new RewardOptionSyncPacket(rewardOptionData.subList(start, end)));
        }
        initializeParts(result);
        return result;
    }

    @Override
    public RewardOptionSyncPacket mergePackets(List<RewardOptionSyncPacket> packets) {
        List<RewardOptionSyncData> merged = new ArrayList<>();
        packets.forEach(packet -> merged.addAll(packet.rewardOptionData));
        return new RewardOptionSyncPacket(merged);
    }

    @Override
    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    private List<RewardOptionSyncPacket> singletonEmptyPart() {
        List<RewardOptionSyncPacket> result = new ArrayList<>();
        result.add(new RewardOptionSyncPacket(Collections.emptyList()));
        initializeParts(result);
        return result;
    }

    private void initializeParts(List<RewardOptionSyncPacket> packets) {
        for (int i = 0; i < packets.size(); i++) {
            RewardOptionSyncPacket packet = packets.get(i);
            packet.setId(getId());
            packet.setSort(i);
            packet.setTotal(packets.size());
        }
    }
}
