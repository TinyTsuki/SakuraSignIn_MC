package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPoolValidator;
import xin.vanilla.sakura.data.lottery.LotteryPools;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.RewardAddPermissionChecker;
import xin.vanilla.sakura.reward.RewardJsonCodec;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.RewardRuleAddPermissionChecker;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 奖池元数据与奖励使用独立分包，由服务端校验并广播权威快照。 */
@Getter
public final class LotteryPoolSyncPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<LotteryPoolSyncPacket>,
        SplitPacket.SplittableSplitPacket<LotteryPoolSyncPacket> {
    private static final int CHUNK_SIZE = 8;
    private static final int MAX_POOLS = 128;
    private static final int MAX_REWARDS = 256;
    private final List<LotteryPool> pools;

    public LotteryPoolSyncPacket(List<LotteryPool> pools) {
        this.pools = LotteryPools.copy(pools);
    }

    public LotteryPoolSyncPacket(BaniraPacketBuffer buffer) {
        super(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_POOLS) {
            throw new IllegalArgumentException("Invalid lottery pool count: " + count);
        }
        pools = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf();
            String name = buffer.readUtf();
            LotteryLimitPolicy policy = buffer.readEnum(LotteryLimitPolicy.class);
            int maxDraws = buffer.readVarInt();
            int cooldown = buffer.readVarInt();
            LotteryPreviewMode previewMode = buffer.readEnum(LotteryPreviewMode.class);
            int rewardCount = buffer.readVarInt();
            if (rewardCount < 0 || rewardCount > MAX_REWARDS) {
                throw new IllegalArgumentException("Invalid lottery reward count: " + rewardCount);
            }
            RewardList rewards = new RewardList();
            for (int rewardIndex = 0; rewardIndex < rewardCount; rewardIndex++) {
                rewards.add(RewardJsonCodec.decode(
                        new com.google.gson.JsonParser().parse(buffer.readUtf())));
            }
            pools.add(new LotteryPool(id, name, policy, maxDraws, cooldown,
                    previewMode, rewards));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        super.toBytes(buffer);
        buffer.writeVarInt(pools.size());
        for (LotteryPool pool : pools) {
            buffer.writeUtf(pool.getId());
            buffer.writeUtf(pool.getDisplayName());
            buffer.writeEnum(pool.getLimitPolicy());
            buffer.writeVarInt(pool.getMaxDraws());
            buffer.writeVarInt(pool.getCooldownSeconds());
            buffer.writeEnum(pool.getPreviewMode());
            buffer.writeVarInt(pool.getRewards().size());
            pool.getRewards().forEach(reward ->
                    buffer.writeUtf(RewardJsonCodec.encode(reward).toString()));
        }
    }

    public static void handle(LotteryPoolSyncPacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        } else {
            context.enqueueWork(() -> applyServerUpdate(packet, context));
        }
        context.markHandled();
    }

    private static void applyServerUpdate(LotteryPoolSyncPacket packet,
                                          BaniraNetworkContext context) {
        ServerPlayerEntity sender = context.senderAs(ServerPlayerEntity.class);
        if (sender == null || !sender.hasPermissions(
                CommonConfig.get().permission().permissionEditReward())
                || !sender.hasPermissions(SakuraUtils.getRewardPermissionLevel(
                ERewardRule.LOTTERY_REWARD))) {
            if (sender != null) {
                BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
            }
            return;
        }
        try {
            Set<String> ids = new HashSet<>();
            if (packet.pools.size() > MAX_POOLS || packet.pools.stream().anyMatch(pool ->
                    !LotteryPoolValidator.validate(pool).isEmpty() || !ids.add(pool.getId()))) {
                throw new IllegalArgumentException("Invalid lottery pool update");
            }
            RewardConfig authoritative = RewardConfigManager.getRewardConfig();
            RewardConfig candidate = RewardConfigManager.deserializeRewardOption(
                    RewardConfigManager.serializeRewardOption(authoritative));
            candidate.setLotteryPools(LotteryPools.copy(packet.pools));
            if (!RewardRuleAddPermissionChecker.canApply(sender, authoritative, candidate)
                    || !RewardAddPermissionChecker.canApply(sender, authoritative, candidate)) {
                throw new IllegalArgumentException("Lottery reward permission denied");
            }
            RewardConfigManager.backupRewardOption(false);
            authoritative.setLotteryPools(LotteryPools.copy(packet.pools));
            RewardConfigManager.saveRewardOption();
            for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
                SakuraNetwork.sendSplitToPlayer(SakuraNetwork.lotteryPoolPacket(player), player);
            }
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(true), sender);
        } catch (RuntimeException failure) {
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
        }
    }

    @Override
    public List<LotteryPoolSyncPacket> splitPacket() {
        if (pools.isEmpty()) {
            List<LotteryPoolSyncPacket> result = Collections.singletonList(
                    new LotteryPoolSyncPacket(Collections.emptyList()));
            initializeParts(result);
            return result;
        }
        List<LotteryPoolSyncPacket> result = new ArrayList<>();
        for (int start = 0; start < pools.size(); start += CHUNK_SIZE) {
            result.add(new LotteryPoolSyncPacket(
                    pools.subList(start, Math.min(start + CHUNK_SIZE, pools.size()))));
        }
        initializeParts(result);
        return result;
    }

    @Override
    public LotteryPoolSyncPacket mergePackets(List<LotteryPoolSyncPacket> packets) {
        List<LotteryPool> merged = new ArrayList<>();
        packets.forEach(packet -> merged.addAll(packet.pools));
        return new LotteryPoolSyncPacket(merged);
    }

    @Override
    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    private void initializeParts(List<LotteryPoolSyncPacket> packets) {
        for (int i = 0; i < packets.size(); i++) {
            LotteryPoolSyncPacket packet = packets.get(i);
            packet.setId(getId());
            packet.setSort(i);
            packet.setTotal(packets.size());
        }
    }
}
