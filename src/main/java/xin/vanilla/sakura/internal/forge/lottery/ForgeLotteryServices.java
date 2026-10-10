package xin.vanilla.sakura.internal.forge.lottery;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.api.cost.CostAccess;
import xin.vanilla.banira.api.cost.InventoryScope;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.api.reward.SakuraRewards;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.internal.forge.storage.ForgeLotteryOperationRepository;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.reward.lottery.*;
import xin.vanilla.sakura.util.SakuraUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/** One operation service per live server, including integrated servers. */
public final class ForgeLotteryServices {
    private static MinecraftServer owner;
    private static LotteryOperationService service;
    private ForgeLotteryServices() { }
    public static LotteryOperationService get(Object actor) {
        ServerPlayerEntity player = (ServerPlayerEntity) actor;
        if (player.server == null || !player.server.isSameThread()) throw new IllegalStateException("Lottery server thread required");
        if (owner != player.server || service == null) {
            owner = player.server; SecureRandom seeds = new SecureRandom();
            service = new LotteryOperationService(new NativeRuntime(),
                    new ForgeLotteryOperationRepository(BaniraDataPaths.worldDataPath()),
                    System::currentTimeMillis, System::nanoTime, RewardConfigManager::getLotteryGeneration,
                    seeds::nextLong, () -> UUID.randomUUID().toString(), ZoneId.systemDefault());
        }
        return service;
    }
    public static boolean isCurrent(Object actor, Object origin) {
        if (!(actor instanceof ServerPlayerEntity) || origin == null) return false;
        ServerPlayerEntity player = (ServerPlayerEntity) actor;
        return new NativeRuntime().connection(player) != null && player.connection.connection == origin;
    }
    public static void disconnect(ServerPlayerEntity player) {
        if (service != null && owner == player.server) service.disconnect(player.getUUID());
    }
    public static void stop() throws IOException {
        try { if (service != null) service.close(); }
        finally { service = null; owner = null; }
    }
    private static final class NativeRuntime implements LotteryOperationService.Runtime {
        public UUID playerId(Object actor) { return ((ServerPlayerEntity) actor).getUUID(); }
        public Object connection(Object actor) {
            if (!(actor instanceof ServerPlayerEntity)) return null;
            ServerPlayerEntity player = (ServerPlayerEntity) actor;
            return player.server != null && player.server.isSameThread() && !player.hasDisconnected()
                    && player.server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive() && !player.removed
                    ? player.connection : null;
        }
        public boolean permitted(Object actor) {
            return ((ServerPlayerEntity) actor).hasPermissions(SakuraUtils.getRewardPermissionLevel(ERewardRule.LOTTERY_REWARD));
        }
        public LotteryPool pool(String id) { return RewardConfigManager.lotteryPool(id); }
        public LotteryDrawState quota(Object actor, String id) {
            return SakuraPlayerData.get(actor).getLotteryDrawStates().stream()
                    .filter(state -> id.equals(state.getPoolId())).findFirst().orElseGet(() -> new LotteryDrawState(id));
        }
        public CostAccess costs(Object actor) { return BaniraCosts.access(actor, InventoryScope.MAIN); }
        public boolean canGrant(Object actor, Reward reward) { return SakuraRewards.find(reward.getTypeId()).isPresent(); }
        public boolean grant(Object actor, Reward reward, LotteryOperation operation) {
            return RewardManager.giveGuaranteedRewardToPlayer((ServerPlayerEntity) actor, SakuraPlayerData.get(actor),
                    reward, new Date(operation.getCreatedAt()), "lottery:" + operation.getPool().getId());
        }
        public void saveQuota(Object actor, LotteryDrawState state) {
            java.util.List<LotteryDrawState> states = SakuraPlayerData.get(actor).getLotteryDrawStates();
            states.removeIf(old -> state.getPoolId().equals(old.getPoolId())); states.add(state);
            try {
                new LotteryQuotaStore(new xin.vanilla.sakura.internal.forge.migration.BaniraPlayerSummaryRepository())
                        .save(playerId(actor), state, () -> SakuraPlayerData.save(actor), () -> SakuraPlayerData.sync(actor));
            } catch (IOException failure) { throw new IllegalStateException("Lottery quota persistence failed", failure); }
        }
        public void completed(Object actor, LotteryOperation operation, LotteryDrawBatchResult result, boolean recovered) {
            LotteryDrawDispatcher.completed((ServerPlayerEntity) actor, operation, result, recovered);
        }
    }
}
