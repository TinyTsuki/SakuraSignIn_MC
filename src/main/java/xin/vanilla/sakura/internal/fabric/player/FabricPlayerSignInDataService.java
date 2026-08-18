package xin.vanilla.sakura.internal.fabric.player;

import xin.vanilla.sakura.data.time.SakuraClock;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInDataRepository;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataMigrationService;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.internal.fabric.migration.BaniraPlayerSummaryRepository;
import xin.vanilla.sakura.internal.fabric.migration.LegacyForgeCapabilityStore;
import xin.vanilla.sakura.internal.fabric.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.platform.SakuraPlayerDataService;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric 1.16.5 玩家对象、旧 Forge 数据迁移与网络同步实现。
 */
public final class FabricPlayerSignInDataService implements SakuraPlayerDataService {
    public static final FabricPlayerSignInDataService INSTANCE = new FabricPlayerSignInDataService();

    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<UUID, IPlayerSignInData> serverCache = new ConcurrentHashMap<>();
    private final Map<UUID, IPlayerSignInData> clientCache = new ConcurrentHashMap<>();

    private FabricPlayerSignInDataService() {
    }

    @Override
    public IPlayerSignInData get(Object playerObject) {
        Player player = requirePlayer(playerObject);
        UUID playerUuid = player.getUUID();
        if (player.getCommandSenderWorld().isClientSide) {
            return clientCache.computeIfAbsent(playerUuid, ignored -> new PlayerSignInData());
        }
        return serverCache.computeIfAbsent(playerUuid, this::loadServerData);
    }

    @Override
    public void setClient(UUID playerUuid, IPlayerSignInData data) {
        PlayerSignInData copy = new PlayerSignInData();
        copy.copyFrom(data);
        clientCache.put(playerUuid, copy);
    }

    @Override
    public LegacyMigrationResult migrateAndLoad(Object playerObject) throws IOException {
        ServerPlayer player = requireServerPlayer(playerObject);
        UUID playerUuid = player.getUUID();
        BaniraPlayerSummaryRepository summaries = new BaniraPlayerSummaryRepository();
        MonthlySignInHistoryRepository histories =
                new MonthlySignInHistoryRepository(BaniraDataPaths.worldDataPath());
        LegacyPlayerDataMigrationService migration = new LegacyPlayerDataMigrationService(
                new LegacyPlayerDataParser(),
                new LegacyForgeCapabilityStore(
                        BaniraDataPaths.vanillaPlayerDataPath(),
                        BaniraDataPaths.worldDataPath()
                ),
                histories,
                summaries
        );
        LegacyMigrationResult result = migration.migrate(playerUuid);
        serverCache.remove(playerUuid);
        applyRetention(playerUuid, histories);
        serverCache.put(playerUuid, new PlayerSignInDataRepository(summaries, histories).load(playerUuid));
        return result;
    }

    @Override
    public void save(Object playerObject) {
        persistOrThrow(requireServerPlayer(playerObject).getUUID());
    }

    @Override
    public void saveAndSync(Object playerObject) {
        ServerPlayer player = requireServerPlayer(playerObject);
        persistOrThrow(player.getUUID());
        sync(player);
    }

    @Override
    public void sync(Object playerObject) {
        ServerPlayer player = requireServerPlayer(playerObject);
        IPlayerSignInData data = get(player);
        String currentMonth = YearMonth.from(
                RewardManager.getCompensateDate(SakuraClock.serverNow())
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
        ).toString();
        SakuraNetwork.sendToPlayer(new PlayerDataSyncPacket(player.getUUID(), data), player);
        SakuraNetwork.sendToPlayer(new PlayerMonthSyncPacket(
                player.getUUID(), currentMonth, data.getSignInRecords()
        ), player);
    }

    @Override
    public void removeServer(UUID playerUuid) {
        try {
            persist(playerUuid);
            serverCache.remove(playerUuid);
        } catch (IOException saveFailure) {
            LOGGER.error("Player cache retained after save failure for {}", playerUuid, saveFailure);
        }
    }

    @Override
    public void clearClient() {
        clientCache.clear();
    }

    @Override
    public void saveAllAndClear() {
        serverCache.keySet().forEach(playerUuid -> {
            try {
                persist(playerUuid);
                serverCache.remove(playerUuid);
            } catch (IOException saveFailure) {
                LOGGER.error("Player cache retained after shutdown save failure for {}",
                        playerUuid, saveFailure);
            }
        });
    }

    private IPlayerSignInData loadServerData(UUID playerUuid) {
        try {
            MonthlySignInHistoryRepository histories =
                    new MonthlySignInHistoryRepository(BaniraDataPaths.worldDataPath());
            applyRetention(playerUuid, histories);
            return repository(histories).load(playerUuid);
        } catch (IOException loadFailure) {
            throw new IllegalStateException("Unable to load Sakura player data: " + playerUuid, loadFailure);
        }
    }

    private void persistOrThrow(UUID playerUuid) {
        try {
            persist(playerUuid);
        } catch (IOException saveFailure) {
            throw new IllegalStateException("Unable to save Sakura player data: " + playerUuid, saveFailure);
        }
    }

    private void persist(UUID playerUuid) throws IOException {
        IPlayerSignInData data = serverCache.get(playerUuid);
        if (data != null) {
            repository().save(playerUuid, data);
        }
    }

    private static PlayerSignInDataRepository repository() {
        return repository(new MonthlySignInHistoryRepository(BaniraDataPaths.worldDataPath()));
    }

    private static PlayerSignInDataRepository repository(MonthlySignInHistoryRepository histories) {
        return new PlayerSignInDataRepository(
                new BaniraPlayerSummaryRepository(),
                histories
        );
    }

    private static void applyRetention(
            UUID playerUuid,
            MonthlySignInHistoryRepository histories
    ) {
        CommonConfig.HistoryView config = CommonConfig.get().history();
        YearMonth currentMonth = YearMonth.from(
                RewardManager.getCompensateDate(SakuraClock.serverNow())
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
        );
        applyRetentionBestEffort(
                playerUuid, histories, currentMonth,
                config.retentionMonths(), config.retentionPolicy()
        );
    }

    static void applyRetentionBestEffort(
            UUID playerUuid,
            MonthlySignInHistoryRepository histories,
            YearMonth currentMonth,
            int retentionMonths,
            HistoryRetentionPolicy policy
    ) {
        try {
            MonthlySignInHistoryRepository.RetentionResult result = histories.applyRetention(
                    playerUuid, currentMonth, retentionMonths, policy
            );
            if (result.getDeletedMonthFiles() > 0 || result.getStrippedMonthFiles() > 0) {
                LOGGER.info("Cleaned Sakura history for {}: stripped={}, deleted={}",
                        playerUuid, result.getStrippedMonthFiles(), result.getDeletedMonthFiles());
            }
        } catch (IOException cleanupFailure) {
            // 保留任务失败不应阻断玩家摘要与当前月详情的加载。
            LOGGER.warn("Unable to clean Sakura history for {}; keeping existing files",
                    playerUuid, cleanupFailure);
        }
    }

    private static Player requirePlayer(Object player) {
        if (!(player instanceof Player)) {
            throw new IllegalArgumentException("Expected Minecraft Player");
        }
        return (Player) player;
    }

    private static ServerPlayer requireServerPlayer(Object player) {
        if (!(player instanceof ServerPlayer)) {
            throw new IllegalArgumentException("Expected Minecraft ServerPlayer");
        }
        return (ServerPlayer) player;
    }
}
