package xin.vanilla.sakura.config.reward;

import xin.vanilla.banira.common.util.NumberUtils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.entity.player.PlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.api.BaniraEnvironment;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.network.data.RewardOptionSyncKind;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.config.reward.LegacyRewardConfigReader;
import xin.vanilla.sakura.config.reward.RewardConfigCodec;
import xin.vanilla.sakura.config.reward.RewardConfigRepository;
import xin.vanilla.sakura.config.reward.RewardGroup;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.banira.common.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Predicate;

public class RewardConfigManager {
    public static final Gson GSON = new GsonBuilder().enableComplexMapKeySerialization().create();

    public static final String FILE_NAME = RewardConfigRepository.REWARD_FILE_NAME;
    private static final String RANDOM_GROUP_SEPARATOR = "#";
    private static final RewardConfigCodec REWARD_CONFIG_CODEC = new RewardConfigCodec();
    private static final LegacyRewardConfigReader LEGACY_REWARD_CONFIG_READER =
            new LegacyRewardConfigReader();
    private static volatile Set<ERewardRule> redactedRules =
            Collections.unmodifiableSet(EnumSet.noneOf(ERewardRule.class));

    private static final Logger LOGGER = LogManager.getLogger();

    public static void updateRedactedRules(RewardOptionSyncPacket packet) {
        EnumSet<ERewardRule> rules = EnumSet.noneOf(ERewardRule.class);
        if (packet != null) {
            packet.getRewardOptionData().stream()
                    .filter(data -> data.getKind() == RewardOptionSyncKind.REDACTED_RULE)
                    .map(RewardOptionSyncData::getRule)
                    .filter(Objects::nonNull)
                    .forEach(rules::add);
        }
        redactedRules = Collections.unmodifiableSet(rules);
    }

    public static boolean isRuleRedacted(ERewardRule rule) {
        return rule != null && redactedRules.contains(rule);
    }

    @NonNull
    private static volatile RewardConfig rewardConfig = new RewardConfig();
    private static volatile RewardConfig clientRewardConfig = new RewardConfig();
    private static volatile java.util.function.BooleanSupplier clientScope = () -> false;

    public static synchronized void configureClientScope(java.util.function.BooleanSupplier scope) {
        try {
            clientRewardConfig = REWARD_CONFIG_CODEC.toRuntimeConfig(REWARD_CONFIG_CODEC.toDocument(rewardConfig));
            clientRewardConfig.refreshContinuousRewardsRelation();
            clientRewardConfig.refreshCycleRewardsRelation();
        } catch (IOException failure) {
            throw new IllegalStateException("Invalid client reward snapshot", failure);
        }
        clientScope = java.util.Objects.requireNonNull(scope);
        clientUndoList.clear(); clientRedoList.clear();
    }

    public static RewardConfig getRewardConfig() {
        return clientScope.getAsBoolean() ? clientRewardConfig : rewardConfig;
    }

    public static void setRewardConfig(@NonNull RewardConfig value) {
        if (clientScope.getAsBoolean()) clientRewardConfig = value;
        else rewardConfig = value;
    }
    @Getter
    private static volatile long lotteryGeneration;
    @Getter
    @Setter
    private static boolean rewardOptionDataChanged = true;

    /**
     * 对 LinkedHashMap 按键排序后替换原内容
     */
    private static void replaceWithSortedMap(LinkedHashMap<String, RewardList> map) {
        LinkedHashMap<String, RewardList> sortedMap = map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(RewardConfigManager::keyComparator))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        // 清空原始 Map 并插入排序后的数据
        map.clear();
        map.putAll(sortedMap);
    }

    /**
     * 自定义排序逻辑，用于比较键
     */
    private static int keyComparator(String key1, String key2) {
        try {
            // 尝试按数字比较
            return Long.compare(Long.parseLong(key1), Long.parseLong(key2));
        } catch (NumberFormatException e) {
            // 如果不是数字，按字母顺序比较
            return key1.compareTo(key2);
        }
    }

    /**
     * 获取配置文件路径
     */
    public static Path getConfigDirectory() {
        Path directory = BaniraDataPaths.gameConfigPath().resolve(SakuraSignIn.MODID);
        return clientScope.getAsBoolean() ? directory.resolve("client-cache") : directory;
    }

    /**
     * 加载 JSON 数据
     */
    public static synchronized void loadRewardOption() {
        {
            try {
                setRewardConfig(repository().loadOrCreate(RewardConfig.getDefault()));
                getRewardConfig().refreshContinuousRewardsRelation();
                getRewardConfig().refreshCycleRewardsRelation();
                if (!clientScope.getAsBoolean()) lotteryGeneration++;
            } catch (Exception e) {
                LOGGER.error("Error loading reward configuration: ", e);
            }
        }
        {
            File undoHistoryFile = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/undo_history.json");
            if (undoHistoryFile.exists()) {
                try {
                    deserializeHistoryFile(undoHistoryFile, undoHistory());
                } catch (Exception e) {
                    LOGGER.error("Error loading undo history: ", e);
                }
            }
            File redoHistoryFile = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/redo_history.json");
            if (redoHistoryFile.exists()) {
                try {
                    deserializeHistoryFile(redoHistoryFile, redoHistory());
                } catch (Exception e) {
                    LOGGER.error("Error loading redo history: ", e);
                }
            }
        }
    }

    private static void deserializeHistoryFile(File redoHistoryFile, Map<ERewardRule, ConcurrentLinkedDeque<String>> history) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(redoHistoryFile.getPath())));
        JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
        for (ERewardRule rule : ERewardRule.values()) {
            JsonArray jsonArray = jsonObject.getAsJsonArray(rule.name().toLowerCase());
            if (jsonArray != null) {
                for (JsonElement jsonElement : jsonArray) {
                    history.computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).add(jsonElement.getAsString());
                }
            }
        }
    }

    private static void serializeHistoryFile(File historyFile, Map<ERewardRule, ConcurrentLinkedDeque<String>> list) {
        JsonObject jsonObject = new JsonObject();
        for (ERewardRule rule : ERewardRule.values()) {
            JsonArray jsonArray = new JsonArray();
            for (String string : list.getOrDefault(rule, new ConcurrentLinkedDeque<>())) {
                jsonArray.add(string);
            }
            jsonObject.add(rule.name().toLowerCase(), jsonArray);
        }
        try (FileWriter writer = new FileWriter(historyFile)) {
            writer.write(GSON.toJson(jsonObject));
        } catch (IOException e) {
            LOGGER.error("Error saving history file: ", e);
        }
    }

    /**
     * 保存 JSON 数据
     */
    public static synchronized void saveRewardOption() {
        {
            try {
                persistRewardOption(repository()::save);
            } catch (IOException e) {
                LOGGER.error("Error saving reward configuration: ", e);
            }
        }
        {
            File dir = new File(RewardConfigManager.getConfigDirectory().toFile(), "history");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File undoHistoryFile = new File(dir, "undo_history.json");
            serializeHistoryFile(undoHistoryFile, undoHistory());
            File redoHistoryFile = new File(dir, "redo_history.json");
            serializeHistoryFile(redoHistoryFile, redoHistory());
        }
    }

    public static void replaceLotteryPools(List<LotteryPool> pools) throws IOException {
        if (clientScope.getAsBoolean()) throw new IOException("Authoritative lottery update requires server scope");
        replaceLotteryPools(pools, repository());
    }

    public static void replaceLotteryPools(List<LotteryPool> pools, long generation) throws IOException {
        if (clientScope.getAsBoolean()) throw new IOException("Authoritative lottery update requires server scope");
        replaceLotteryPools(pools, generation, repository());
    }

    static synchronized void replaceLotteryPools(List<LotteryPool> pools, long generation,
                                                  RewardConfigRepository repository) throws IOException {
        if (generation != lotteryGeneration) throw new IOException("Stale lottery configuration revision");
        replaceLotteryPools(pools, repository);
    }

    static synchronized void replaceLotteryPools(List<LotteryPool> pools,
                                                  RewardConfigRepository repository) throws IOException {
        replaceLotteryPools(pools, candidate -> {
            repository.backupCurrent("lottery-update");
            repository.save(candidate);
        });
    }

    @FunctionalInterface
    interface RewardConfigPersistence {
        void save(RewardConfig candidate) throws IOException;
    }

    static synchronized void replaceLotteryPools(List<LotteryPool> pools,
                                                  RewardConfigPersistence persistence) throws IOException {
        if (clientScope.getAsBoolean()) throw new IOException("Authoritative lottery update requires server scope");
        RewardConfig candidate = REWARD_CONFIG_CODEC.decode(REWARD_CONFIG_CODEC.encode(getRewardConfig()));
        candidate.setLotteryPools(pools);
        candidate = REWARD_CONFIG_CODEC.decode(REWARD_CONFIG_CODEC.encode(candidate));
        candidate.refreshContinuousRewardsRelation();
        candidate.refreshCycleRewardsRelation();
        persistence.save(candidate);
        rewardConfig = candidate;
        lotteryGeneration++;
        rewardOptionDataChanged = true;
    }

    static synchronized void persistRewardOption(RewardConfigPersistence persistence) throws IOException {
        persistence.save(getRewardConfig());
        if (!clientScope.getAsBoolean()) lotteryGeneration++;
    }

    public static void replaceRewardConfig(RewardConfig candidate, long generation) throws IOException {
        if (clientScope.getAsBoolean()) throw new IOException("Authoritative reward update requires server scope");
        RewardConfigRepository storage = repository();
        replaceRewardConfig(candidate, generation, value -> {
            storage.backupCurrent("reward-upload"); storage.save(value);
        });
    }

    static synchronized void replaceRewardConfig(RewardConfig candidate, long generation,
                                                  RewardConfigPersistence persistence) throws IOException {
        if (clientScope.getAsBoolean() || generation != lotteryGeneration)
            throw new IOException("Stale or invalid reward update scope");
        RewardConfig validated = REWARD_CONFIG_CODEC.toRuntimeConfig(REWARD_CONFIG_CODEC.toDocument(candidate));
        validated.refreshContinuousRewardsRelation(); validated.refreshCycleRewardsRelation();
        persistence.save(validated);
        rewardConfig = validated; lotteryGeneration++; rewardOptionDataChanged = true;
    }

    public static RewardConfig mergeUpload(RewardConfig incoming, RewardConfig authoritative,
                                          Predicate<ERewardRule> canView) throws IOException {
        RewardConfig result = REWARD_CONFIG_CODEC.toRuntimeConfig(REWARD_CONFIG_CODEC.toDocument(incoming));
        for (ERewardRule rule : ERewardRule.values()) {
            if (canView.test(rule)) continue;
            if (rule == ERewardRule.LOTTERY_REWARD) {
                result.setLotteryPools(xin.vanilla.sakura.data.lottery.LotteryPools.copy(authoritative.getLotteryPools()));
            } else if (rule == ERewardRule.PERSONAL_DATE_REWARD) {
                result.setPersonalDatePresets(xin.vanilla.sakura.data.personaldate.PersonalDatePresets.copy(
                        authoritative.getPersonalDatePresets()));
            } else {
                Map<String, RewardList> preserved = new LinkedHashMap<>();
                getRewardMap(authoritative, rule).forEach((key, rewards) -> preserved.put(key, rewards.clone()));
                setRewardMap(result, rule, preserved);
            }
        }
        return result;
    }


    private static final Random random = new Random();
    /**
     * 撤销列表
     */
    private static final Map<ERewardRule, ConcurrentLinkedDeque<String>> undoList = new ConcurrentHashMap<>();
    /**
     * 恢复列表
     */
    private static final Map<ERewardRule, ConcurrentLinkedDeque<String>> redoList = new ConcurrentHashMap<>();
    private static final Map<ERewardRule, ConcurrentLinkedDeque<String>> clientUndoList = new ConcurrentHashMap<>();
    private static final Map<ERewardRule, ConcurrentLinkedDeque<String>> clientRedoList = new ConcurrentHashMap<>();
    private static Map<ERewardRule, ConcurrentLinkedDeque<String>> undoHistory() {
        return clientScope.getAsBoolean() ? clientUndoList : undoList;
    }
    private static Map<ERewardRule, ConcurrentLinkedDeque<String>> redoHistory() {
        return clientScope.getAsBoolean() ? clientRedoList : redoList;
    }

    public static void clearUndoList() {
        undoHistory().clear();
    }

    public static void clearRedoList() {
        redoHistory().clear();
    }

    /**
     * 添加撤销数据
     * 数据修改前调用
     */
    public static void addUndoRewardOption(ERewardRule rule) {
        File dir = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/undo/" + rule.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String historyId = System.currentTimeMillis() + "." + random.nextInt(1000000);
        File dataFile = new File(dir, historyId + "." + FILE_NAME);
        try (FileWriter writer = new FileWriter(dataFile)) {
            Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
            writer.write(gson.toJson(getRewardMap(rule)));
            undoHistory().computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).push(historyId);
        } catch (IOException e) {
            LOGGER.error("Error saving undo data: ", e);
        }
        // 删除旧文件
        deleteOldFile(dir, 100);
    }

    /**
     * 获取撤销数据
     * Ctrl Z 时调用
     */
    public static Map<String, RewardList> getUnDoRewardOption(ERewardRule rule) {
        File dir = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/undo/" + rule.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 移除队列末尾
        File dataFile = new File(dir, undoHistory().getOrDefault(rule, new ConcurrentLinkedDeque<>()).poll() + "." + FILE_NAME);
        if (!dataFile.exists()) {
            return new LinkedHashMap<>();
        }
        try {
            String jsonString = new String(Files.readAllBytes(Paths.get(dataFile.getPath())));
            return GSON.fromJson(jsonString, new TypeToken<LinkedHashMap<String, RewardList>>() {
            }.getType());
        } catch (IOException e) {
            LOGGER.error("Error loading undo data: ", e);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 添加重做数据
     * 撤销时修改数据前调用
     */
    public static void addRedoRewardOption(ERewardRule rule) {
        File dir = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/redo/" + rule.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String historyId = System.currentTimeMillis() + "." + random.nextInt(1000000);
        File dataFile = new File(dir, historyId + "." + FILE_NAME);
        try (FileWriter writer = new FileWriter(dataFile)) {
            Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
            writer.write(gson.toJson(getRewardMap(rule)));
            redoHistory().computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).push(historyId);
        } catch (IOException e) {
            LOGGER.error("Error saving redo data: ", e);
        }
        // 删除旧文件
        deleteOldFile(dir, 100);
    }

    /**
     * 获取重做数据
     * Ctrl Shift Z 时调用
     */
    public static Map<String, RewardList> getReDoRewardOption(ERewardRule rule) {
        File dir = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/redo/" + rule.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dataFile = new File(dir, redoHistory().getOrDefault(rule, new ConcurrentLinkedDeque<>()).poll() + "." + FILE_NAME);
        if (!dataFile.exists()) {
            return new LinkedHashMap<>();
        }
        try {
            String jsonString = new String(Files.readAllBytes(Paths.get(dataFile.getPath())));
            return GSON.fromJson(jsonString, new TypeToken<LinkedHashMap<String, RewardList>>() {
            }.getType());
        } catch (IOException e) {
            LOGGER.error("Error loading redo data: ", e);
        }
        return new LinkedHashMap<>();
    }

    /**
     * 备份 JSON 数据
     */
    public static void backupRewardOption() {
        RewardConfigManager.backupRewardOption(true);
    }

    /**
     * 备份 JSON 数据
     */
    public static void backupRewardOption(boolean save) {
        try {
            repository().backupCurrent(save ? "client-before-sync" : "server-before-sync");
        } catch (IOException e) {
            LOGGER.error("Error backing up reward configuration: ", e);
        }
    }

    /**
     * 随机组的内部键附带列表索引，界面只显示概率部分。
     */
    public static String getDisplayKey(ERewardRule rule, String key) {
        return rule == ERewardRule.RANDOM_REWARD ? randomProbability(key) : key;
    }

    private static String randomGroupToken(String probability, int index) {
        return probability + RANDOM_GROUP_SEPARATOR + index;
    }

    private static String randomProbability(String key) {
        if (key == null) {
            return "";
        }
        int separator = key.lastIndexOf(RANDOM_GROUP_SEPARATOR);
        if (separator < 0) {
            return key;
        }
        try {
            Integer.parseInt(key.substring(separator + RANDOM_GROUP_SEPARATOR.length()));
            return key.substring(0, separator);
        } catch (NumberFormatException ignored) {
            return key;
        }
    }

    private static int randomGroupIndex(String key) {
        if (key == null) {
            return -1;
        }
        int separator = key.lastIndexOf(RANDOM_GROUP_SEPARATOR);
        if (separator >= 0) {
            try {
                int index = Integer.parseInt(
                        key.substring(separator + RANDOM_GROUP_SEPARATOR.length()));
                if (index >= 0 && index < getRewardConfig().getRandomRewardGroups().size()
                        && getRewardConfig().getRandomRewardGroups().get(index).getKey()
                        .equals(key.substring(0, separator))) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String probability = randomProbability(key);
        for (int i = 0; i < getRewardConfig().getRandomRewardGroups().size(); i++) {
            if (getRewardConfig().getRandomRewardGroups().get(i).getKey().equals(probability)) {
                return i;
            }
        }
        return -1;
    }

    private static RewardGroup randomGroup(String key) {
        int index = randomGroupIndex(key);
        return index < 0 ? null : getRewardConfig().getRandomRewardGroups().get(index);
    }

    private static Map<String, RewardList> randomRewardMap(RewardConfig config) {
        Map<String, RewardList> result = new LinkedHashMap<>();
        for (int i = 0; i < config.getRandomRewardGroups().size(); i++) {
            RewardGroup group = config.getRandomRewardGroups().get(i);
            result.put(randomGroupToken(group.getKey(), i), group.getRewards());
        }
        return result;
    }

    private static RewardConfigRepository repository() {
        return new RewardConfigRepository(getConfigDirectory());
    }

    private static void deleteOldFile(File dir, int num) {
        try (Stream<Path> pathStream = Files.walk(dir.toPath())) {
            pathStream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(RewardConfigManager.FILE_NAME))
                    .sorted((path1, path2) -> {
                        try {
                            return Files.readAttributes(path2, BasicFileAttributes.class).creationTime()
                                    .compareTo(Files.readAttributes(path1, BasicFileAttributes.class).creationTime());
                        } catch (IOException e) {
                            LOGGER.error("Error reading file attributes: ", e);
                            return 0;
                        }
                    })
                    // 跳过最新的num个文件
                    .skip(num)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            LOGGER.error("Error deleting file: ", e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error walking directory: ", e);
        }
    }

    /**
     * 校验 keyName 是否有效
     *
     * @param rule    规则类型
     * @param keyName 键名
     */
    public static boolean validateKeyName(@NonNull ERewardRule rule, @NonNull String keyName) {
        boolean result;
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD:
            case CYCLE_REWARD:
                result = NumberUtils.toInt(keyName) > 0;
                break;
            case YEAR_REWARD: {
                int anInt = NumberUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 366;
            }
            break;
            case MONTH_REWARD: {
                int anInt = NumberUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 31;
            }
            break;
            case WEEK_REWARD: {
                int anInt = NumberUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 7;
            }
            break;
            case DATE_TIME_REWARD:
                result = !RewardConfig.parseDateRange(keyName).isEmpty();
                break;
            case CUMULATIVE_REWARD: {
                int anInt = NumberUtils.toInt(keyName);
                result = anInt > 0;
            }
            break;
            case RANDOM_REWARD: {
                BigDecimal property = NumberUtils.toBigDecimal(randomProbability(keyName));
                result = property.compareTo(new BigDecimal("0.0000000001")) >= 0 && property.compareTo(BigDecimal.ONE) <= 0;
            }
            break;
            case CDK_REWARD: {
                result = Pattern.compile("^\\w+$").matcher(keyName).matches();
            }
            break;
            case PERSONAL_DATE_REWARD:
            case LOTTERY_REWARD:
                result = keyName.matches("[a-z0-9_.-]{1,64}");
                break;
            default:
                result = false;
        }
        return result;
    }

    /**
     * 获取奖励规则
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    @NonNull
    public static RewardList getKeyName(@NonNull ERewardRule rule, @NonNull String keyName) {
        RewardList result;
        switch (rule) {
            case BASE_REWARD:
                result = getRewardConfig().getBaseRewards();
                break;
            case CONTINUOUS_REWARD:
                result = getRewardConfig().getContinuousRewards().get(keyName);
                break;
            case CYCLE_REWARD:
                result = getRewardConfig().getCycleRewards().get(keyName);
                break;
            case YEAR_REWARD:
                result = getRewardConfig().getYearRewards().get(keyName);
                break;
            case MONTH_REWARD:
                result = getRewardConfig().getMonthRewards().get(keyName);
                break;
            case WEEK_REWARD:
                result = getRewardConfig().getWeekRewards().get(keyName);
                break;
            case DATE_TIME_REWARD:
                result = getRewardConfig().getDateTimeRewards().get(keyName);
                break;
            case CUMULATIVE_REWARD:
                result = getRewardConfig().getCumulativeRewards().get(keyName);
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                result = randomGroup == null ? null : randomGroup.getRewards();
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int key = getRewardConfig().getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    key = NumberUtils.toInt(split[2]);
                }
                if (getRewardConfig().getCdkRewards().size() <= key || key < 0) {
                    result = null;
                } else {
                    result = getRewardConfig().getCdkRewards().get(key).value().key();
                }
                break;
            case PERSONAL_DATE_REWARD:
                PersonalDatePreset personalPreset = personalDatePreset(keyName);
                result = personalPreset == null ? null : personalPreset.getRewards();
                break;
            case LOTTERY_REWARD:
                LotteryPool lotteryPool = lotteryPool(keyName);
                result = lotteryPool == null ? null : lotteryPool.getRewards();
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
        return result == null ? new RewardList() : result;
    }

    /**
     * 添加奖励规则
     *
     * @param rule       规则类型
     * @param keyName    规则
     * @param rewardList 奖励列表
     */
    public static void addKeyName(@NonNull ERewardRule rule, @NonNull String keyName, @NonNull RewardList rewardList) {
        switch (rule) {
            case BASE_REWARD:
                getRewardConfig().getBaseRewards().addAll(rewardList);
                break;
            case CONTINUOUS_REWARD:
                getRewardConfig().addContinuousRewards(keyName, rewardList);
                break;
            case CYCLE_REWARD:
                getRewardConfig().addCycleRewards(keyName, rewardList);
                break;
            case YEAR_REWARD:
                getRewardConfig().addYearRewards(keyName, rewardList);
                break;
            case MONTH_REWARD:
                getRewardConfig().addMonthRewards(keyName, rewardList);
                break;
            case WEEK_REWARD:
                getRewardConfig().addWeekRewards(keyName, rewardList);
                break;
            case DATE_TIME_REWARD:
                getRewardConfig().addDateTimeRewards(keyName, rewardList);
                break;
            case CUMULATIVE_REWARD:
                getRewardConfig().addCumulativeReward(keyName, rewardList);
                break;
            case RANDOM_REWARD:
                RewardGroup existingRandomGroup = keyName.contains(RANDOM_GROUP_SEPARATOR)
                        ? randomGroup(keyName)
                        : null;
                if (existingRandomGroup == null) {
                    getRewardConfig().addRandomRewardGroup(randomProbability(keyName), rewardList);
                } else {
                    existingRandomGroup.getRewards().addAll(rewardList);
                }
                break;
            case CDK_REWARD:
                String date = getCdkRewardDate(keyName);
                int index = getCdkRewardIndex(keyName);
                if (getRewardConfig().getCdkRewards().size() <= index || index < 0) {
                    getRewardConfig().addCdkReward(new KeyValue<>(new KeyValue<>(getCdkRewardKey(keyName), date), new KeyValue<>(rewardList, new AtomicInteger(getCdkRewardNum(keyName)))));
                } else {
                    getRewardConfig().getCdkRewards().get(index).value().key().addAll(rewardList);
                }
                break;
            case PERSONAL_DATE_REWARD:
                PersonalDatePreset personalPreset = personalDatePreset(keyName);
                if (personalPreset != null) {
                    personalPreset.getRewards().addAll(rewardList);
                }
                break;
            case LOTTERY_REWARD:
                LotteryPool lotteryPool = lotteryPool(keyName);
                if (lotteryPool == null) {
                    getRewardConfig().getLotteryPools().add(new LotteryPool(keyName, keyName,
                            LotteryLimitPolicy.DAILY, 1, 0, rewardList));
                } else {
                    lotteryPool.getRewards().addAll(rewardList);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 修改奖励规则的 keyName
     *
     * @param rule       规则类型
     * @param oldKeyName 旧的 keyName
     * @param newKeyName 新的 keyName
     */
    public static void updateKeyName(@NonNull ERewardRule rule, @NonNull String oldKeyName, @NonNull String newKeyName) {
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD: {
                RewardList remove = getRewardConfig().getContinuousRewards().remove(oldKeyName);
                getRewardConfig().addContinuousRewards(newKeyName, remove);
            }
            break;
            case CYCLE_REWARD: {
                RewardList remove = getRewardConfig().getCycleRewards().remove(oldKeyName);
                getRewardConfig().addCycleRewards(newKeyName, remove);
            }
            break;
            case YEAR_REWARD: {
                RewardList remove = getRewardConfig().getYearRewards().remove(oldKeyName);
                getRewardConfig().addYearRewards(newKeyName, remove);
            }
            break;
            case MONTH_REWARD: {
                RewardList remove = getRewardConfig().getMonthRewards().remove(oldKeyName);
                getRewardConfig().addMonthRewards(newKeyName, remove);
            }
            break;
            case WEEK_REWARD: {
                RewardList remove = getRewardConfig().getWeekRewards().remove(oldKeyName);
                getRewardConfig().addWeekRewards(newKeyName, remove);
            }
            break;
            case DATE_TIME_REWARD: {
                RewardList remove = getRewardConfig().getDateTimeRewards().remove(oldKeyName);
                getRewardConfig().addDateTimeRewards(newKeyName, remove);
            }
            break;
            case CUMULATIVE_REWARD: {
                RewardList remove = getRewardConfig().getCumulativeRewards().remove(oldKeyName);
                getRewardConfig().addCumulativeReward(newKeyName, remove);
            }
            break;
            case RANDOM_REWARD: {
                RewardGroup group = randomGroup(oldKeyName);
                if (group != null) {
                    group.setKey(randomProbability(newKeyName));
                }
            }
            break;
            case CDK_REWARD: {
                String[] oldSplit = oldKeyName.replaceAll("\\|", ",").split(",");
                int oldIndex = getRewardConfig().getCdkRewards().size();
                if (oldSplit.length == 3 || oldSplit.length == 4) {
                    oldIndex = NumberUtils.toInt(oldSplit[2]);
                }
                if (getRewardConfig().getCdkRewards().size() > oldIndex) {
                    String[] split = newKeyName.replaceAll("\\|", ",").split(",");
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> remove = getRewardConfig().getCdkRewards().remove(oldIndex);
                    remove.key().key(split[0]);
                    remove.key().value(split[1]);
                    remove.value().value().set(NumberUtils.toInt(split[3], 1));
                    getRewardConfig().addCdkReward(remove);
                }
            }
            break;
            case LOTTERY_REWARD: {
                LotteryPool pool = lotteryPool(oldKeyName);
                if (pool != null && validateKeyName(rule, newKeyName)) {
                    pool.setId(newKeyName);
                }
            }
            break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 清空奖励规则 keyName 下的奖励列表
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    public static void clearKey(@NonNull ERewardRule rule, @NonNull String keyName) {
        switch (rule) {
            case BASE_REWARD:
                getRewardConfig().getBaseRewards().clear();
                break;
            case CONTINUOUS_REWARD:
                getRewardConfig().getContinuousRewards().get(keyName).clear();
                break;
            case CYCLE_REWARD:
                getRewardConfig().getCycleRewards().get(keyName).clear();
                break;
            case YEAR_REWARD:
                getRewardConfig().getYearRewards().get(keyName).clear();
                break;
            case MONTH_REWARD:
                getRewardConfig().getMonthRewards().get(keyName).clear();
                break;
            case WEEK_REWARD:
                getRewardConfig().getWeekRewards().get(keyName).clear();
                break;
            case DATE_TIME_REWARD:
                getRewardConfig().getDateTimeRewards().get(keyName).clear();
                break;
            case CUMULATIVE_REWARD:
                getRewardConfig().getCumulativeRewards().get(keyName).clear();
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                if (randomGroup != null) {
                    randomGroup.getRewards().clear();
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = getRewardConfig().getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = NumberUtils.toInt(split[2]);
                }
                if (getRewardConfig().getCdkRewards().size() > index) {
                    getRewardConfig().getCdkRewards().get(index).value().key().clear();
                }
                break;
            case PERSONAL_DATE_REWARD:
                PersonalDatePreset personalPreset = personalDatePreset(keyName);
                if (personalPreset != null) {
                    personalPreset.getRewards().clear();
                }
                break;
            case LOTTERY_REWARD:
                LotteryPool lotteryPool = lotteryPool(keyName);
                if (lotteryPool != null) {
                    lotteryPool.getRewards().clear();
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 删除奖励规则
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    public static void deleteKey(@NonNull ERewardRule rule, @NonNull String keyName) {
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD:
                getRewardConfig().getContinuousRewards().remove(keyName);
                break;
            case CYCLE_REWARD:
                getRewardConfig().getCycleRewards().remove(keyName);
                break;
            case YEAR_REWARD:
                getRewardConfig().getYearRewards().remove(keyName);
                break;
            case MONTH_REWARD:
                getRewardConfig().getMonthRewards().remove(keyName);
                break;
            case WEEK_REWARD:
                getRewardConfig().getWeekRewards().remove(keyName);
                break;
            case DATE_TIME_REWARD:
                getRewardConfig().getDateTimeRewards().remove(keyName);
                break;
            case CUMULATIVE_REWARD:
                getRewardConfig().getCumulativeRewards().remove(keyName);
                break;
            case RANDOM_REWARD:
                int randomIndex = randomGroupIndex(keyName);
                if (randomIndex >= 0) {
                    getRewardConfig().getRandomRewardGroups().remove(randomIndex);
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = getRewardConfig().getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = NumberUtils.toInt(split[2]);
                }
                if (getRewardConfig().getCdkRewards().size() > index) {
                    getRewardConfig().getCdkRewards().remove(index);
                }
                break;
            case PERSONAL_DATE_REWARD:
                getRewardConfig().getPersonalDatePresets().removeIf(
                        preset -> keyName.equals(preset.getId()));
                break;
            case LOTTERY_REWARD:
                getRewardConfig().getLotteryPools().removeIf(
                        pool -> keyName.equals(pool.getId()));
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 获取奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     */
    @NonNull
    public static Reward getReward(ERewardRule rule, String keyName, int index) {
        Reward result;
        try {
            switch (rule) {
                case BASE_REWARD:
                    result = getRewardConfig().getBaseRewards().get(index);
                    break;
                case CONTINUOUS_REWARD:
                    result = getRewardConfig().getContinuousRewards().get(keyName).get(index);
                    break;
                case CYCLE_REWARD:
                    result = getRewardConfig().getCycleRewards().get(keyName).get(index);
                    break;
                case YEAR_REWARD:
                    result = getRewardConfig().getYearRewards().get(keyName).get(index);
                    break;
                case MONTH_REWARD:
                    result = getRewardConfig().getMonthRewards().get(keyName).get(index);
                    break;
                case WEEK_REWARD:
                    result = getRewardConfig().getWeekRewards().get(keyName).get(index);
                    break;
                case DATE_TIME_REWARD:
                    result = getRewardConfig().getDateTimeRewards().get(keyName).get(index);
                    break;
                case CUMULATIVE_REWARD:
                    result = getRewardConfig().getCumulativeRewards().get(keyName).get(index);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    result = randomGroup == null ? null : randomGroup.getRewards().get(index);
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = getRewardConfig().getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                    }
                    if (getRewardConfig().getCdkRewards().size() <= key || key < 0) {
                        result = null;
                    } else {
                        result = getRewardConfig().getCdkRewards().get(key).value().key().get(index);
                    }
                    break;
                case PERSONAL_DATE_REWARD:
                    PersonalDatePreset personalPreset = personalDatePreset(keyName);
                    result = personalPreset == null ? null : personalPreset.getRewards().get(index);
                    break;
                case LOTTERY_REWARD:
                    LotteryPool lotteryPool = lotteryPool(keyName);
                    result = lotteryPool == null ? null : lotteryPool.getRewards().get(index);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception ignored) {
            result = new Reward();
        }
        return result == null ? new Reward() : result;
    }

    /**
     * 添加奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param reward  奖励
     */
    public static void addReward(ERewardRule rule, String keyName, Reward reward) {
        if (StringUtils.isNullOrEmpty(keyName)) return;
        switch (rule) {
            case BASE_REWARD:
                getRewardConfig().getBaseRewards().add(reward);
                break;
            case CONTINUOUS_REWARD:
                if (!getRewardConfig().getContinuousRewards().containsKey(keyName)) {
                    getRewardConfig().getContinuousRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getContinuousRewards().get(keyName).add(reward);
                break;
            case CYCLE_REWARD:
                if (!getRewardConfig().getCycleRewards().containsKey(keyName)) {
                    getRewardConfig().getCycleRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getCycleRewards().get(keyName).add(reward);
                break;
            case YEAR_REWARD:
                if (!getRewardConfig().getYearRewards().containsKey(keyName)) {
                    getRewardConfig().getYearRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getYearRewards().get(keyName).add(reward);
                break;
            case MONTH_REWARD:
                if (!getRewardConfig().getMonthRewards().containsKey(keyName)) {
                    getRewardConfig().getMonthRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getMonthRewards().get(keyName).add(reward);
                break;
            case WEEK_REWARD:
                if (!getRewardConfig().getWeekRewards().containsKey(keyName)) {
                    getRewardConfig().getWeekRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getWeekRewards().get(keyName).add(reward);
                break;
            case DATE_TIME_REWARD:
                if (!getRewardConfig().getDateTimeRewards().containsKey(keyName)) {
                    getRewardConfig().getDateTimeRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getDateTimeRewards().get(keyName).add(reward);
                break;
            case CUMULATIVE_REWARD:
                if (!getRewardConfig().getCumulativeRewards().containsKey(keyName)) {
                    getRewardConfig().getCumulativeRewards().put(keyName, new RewardList());
                }
                getRewardConfig().getCumulativeRewards().get(keyName).add(reward);
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                if (randomGroup == null) {
                    getRewardConfig().addRandomRewardGroup(
                            randomProbability(keyName),
                            new RewardList(Collections.singletonList(reward)));
                } else {
                    randomGroup.getRewards().add(reward);
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                String date = "";
                int key = getRewardConfig().getCdkRewards().size();
                int num = 1;
                if (split.length == 3 || split.length == 4) {
                    date = split[1];
                    key = NumberUtils.toInt(split[2]);
                    if (split.length == 4) {
                        num = NumberUtils.toInt(split[3], 1);
                    }
                }
                if (getRewardConfig().getCdkRewards().size() <= key || key < 0) {
                    getRewardConfig().getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], date), new KeyValue<>(new RewardList() {{
                        add(reward);
                    }}, new AtomicInteger(num))));
                } else {
                    getRewardConfig().getCdkRewards().get(key).value().key().add(reward);
                }
                break;
            case PERSONAL_DATE_REWARD:
                PersonalDatePreset personalPreset = personalDatePreset(keyName);
                if (personalPreset != null) {
                    personalPreset.getRewards().add(reward);
                }
                break;
            case LOTTERY_REWARD:
                LotteryPool lotteryPool = lotteryPool(keyName);
                if (lotteryPool != null) {
                    lotteryPool.getRewards().add(reward);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 更新奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     * @param reward  奖励
     */
    public static void updateReward(ERewardRule rule, String keyName, int index, Reward reward) {
        try {
            switch (rule) {
                case BASE_REWARD:
                    getRewardConfig().getBaseRewards().set(index, reward);
                    break;
                case CONTINUOUS_REWARD:
                    if (!getRewardConfig().getContinuousRewards().containsKey(keyName)) {
                        getRewardConfig().getContinuousRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getContinuousRewards().get(keyName).set(index, reward);
                    break;
                case CYCLE_REWARD:
                    if (!getRewardConfig().getCycleRewards().containsKey(keyName)) {
                        getRewardConfig().getCycleRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getCycleRewards().get(keyName).set(index, reward);
                    break;
                case YEAR_REWARD:
                    if (!getRewardConfig().getYearRewards().containsKey(keyName)) {
                        getRewardConfig().getYearRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getYearRewards().get(keyName).set(index, reward);
                    break;
                case MONTH_REWARD:
                    if (!getRewardConfig().getMonthRewards().containsKey(keyName)) {
                        getRewardConfig().getMonthRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getMonthRewards().get(keyName).set(index, reward);
                    break;
                case WEEK_REWARD:
                    if (!getRewardConfig().getWeekRewards().containsKey(keyName)) {
                        getRewardConfig().getWeekRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getWeekRewards().get(keyName).set(index, reward);
                    break;
                case DATE_TIME_REWARD:
                    if (!getRewardConfig().getDateTimeRewards().containsKey(keyName)) {
                        getRewardConfig().getDateTimeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getDateTimeRewards().get(keyName).set(index, reward);
                    break;
                case CUMULATIVE_REWARD:
                    if (!getRewardConfig().getCumulativeRewards().containsKey(keyName)) {
                        getRewardConfig().getCumulativeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    getRewardConfig().getCumulativeRewards().get(keyName).set(index, reward);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    if (randomGroup == null) {
                        getRewardConfig().addRandomRewardGroup(
                                randomProbability(keyName),
                                new RewardList(Collections.singletonList(reward)));
                    } else {
                        randomGroup.getRewards().set(index, reward);
                    }
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = getRewardConfig().getCdkRewards().size();
                    int num = 1;
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                        if (split.length == 4) {
                            num = NumberUtils.toInt(split[3], 1);
                        }
                    }
                    if (getRewardConfig().getCdkRewards().size() <= key || key < 0) {
                        getRewardConfig().getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList() {{
                            add(reward);
                        }}, new AtomicInteger(num))));
                    } else {
                        getRewardConfig().getCdkRewards().get(key).value().key().set(index, reward);
                    }
                    break;
                case PERSONAL_DATE_REWARD:
                    PersonalDatePreset personalPreset = personalDatePreset(keyName);
                    if (personalPreset != null) {
                        personalPreset.getRewards().set(index, reward);
                    }
                    break;
                case LOTTERY_REWARD:
                    LotteryPool lotteryPool = lotteryPool(keyName);
                    if (lotteryPool != null) {
                        lotteryPool.getRewards().set(index, reward);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 删除奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     */
    public static void deleteReward(ERewardRule rule, String keyName, int index) {
        try {
            switch (rule) {
                case BASE_REWARD:
                    getRewardConfig().getBaseRewards().remove(index);
                    break;
                case CONTINUOUS_REWARD:
                    getRewardConfig().getContinuousRewards().get(keyName).remove(index);
                    break;
                case CYCLE_REWARD:
                    getRewardConfig().getCycleRewards().get(keyName).remove(index);
                    break;
                case YEAR_REWARD:
                    getRewardConfig().getYearRewards().get(keyName).remove(index);
                    break;
                case MONTH_REWARD:
                    getRewardConfig().getMonthRewards().get(keyName).remove(index);
                    break;
                case WEEK_REWARD:
                    getRewardConfig().getWeekRewards().get(keyName).remove(index);
                    break;
                case DATE_TIME_REWARD:
                    getRewardConfig().getDateTimeRewards().get(keyName).remove(index);
                    break;
                case CUMULATIVE_REWARD:
                    getRewardConfig().getCumulativeRewards().get(keyName).remove(index);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    if (randomGroup != null) {
                        randomGroup.getRewards().remove(index);
                    }
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = getRewardConfig().getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                    }
                    if (getRewardConfig().getCdkRewards().size() <= key || key < 0) {
                        getRewardConfig().getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList(), new AtomicInteger(1))));
                    } else {
                        getRewardConfig().getCdkRewards().get(key).value().key().remove(index);
                    }
                    break;
                case PERSONAL_DATE_REWARD:
                    PersonalDatePreset personalPreset = personalDatePreset(keyName);
                    if (personalPreset != null) {
                        personalPreset.getRewards().remove(index);
                    }
                    break;
                case LOTTERY_REWARD:
                    LotteryPool lotteryPool = lotteryPool(keyName);
                    if (lotteryPool != null) {
                        lotteryPool.getRewards().remove(index);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception e) {
            LOGGER.error("Error deleting reward: ", e);
        }
    }

    /**
     * 排序奖励配置
     */
    public static void sortRewards() {
        RewardConfigManager.sortRewards(null);
    }

    /**
     * 排序奖励配置
     *
     * @param rule 规则类型
     */
    public static void sortRewards(ERewardRule rule) {
        List<ERewardRule> rules;
        if (rule == null) {
            rules = Arrays.asList(ERewardRule.values());
        } else {
            rules = Collections.singletonList(rule);
        }
        for (ERewardRule rewardRule : rules) {
            switch (rewardRule) {
                case BASE_REWARD:
                    break;
                case CONTINUOUS_REWARD:
                    // 对键排序并替换原始 Map 的内容
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getContinuousRewards());
                    break;
                case CYCLE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getCycleRewards());
                    break;
                case YEAR_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getYearRewards());
                    break;
                case MONTH_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getMonthRewards());
                    break;
                case WEEK_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getWeekRewards());
                    break;
                case DATE_TIME_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getDateTimeRewards());
                    break;
                case CUMULATIVE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) getRewardConfig().getCumulativeRewards());
                    break;
                case RANDOM_REWARD:
                    getRewardConfig().getRandomRewardGroups().sort(
                            Comparator.comparing(RewardGroup::getKey, RewardConfigManager::keyComparator));
                    break;
                case CDK_REWARD:
                    getRewardConfig().getCdkRewards().sort(Comparator.comparing(keyVal -> keyVal.key().key()));
                    break;
                case PERSONAL_DATE_REWARD:
                    getRewardConfig().getPersonalDatePresets().sort(
                            Comparator.comparing(PersonalDatePreset::getId));
                    break;
                case LOTTERY_REWARD:
                    getRewardConfig().getLotteryPools().sort(
                            Comparator.comparing(LotteryPool::getId));
                    break;
            }
        }
    }

    /**
     * 序列化 RewardOption
     */
    public static String serializeRewardOption(RewardConfig rewardConfig) {
        try {
            return REWARD_CONFIG_CODEC.encode(rewardConfig);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize reward configuration", e);
        }
    }

    /**
     * 反序列化 RewardOption
     */
    @NonNull
    public static RewardConfig deserializeRewardOption(String jsonString) {
        if (StringUtils.isNullOrEmpty(jsonString)) {
            return RewardConfig.getDefault();
        }
        try {
            JsonObject root = new JsonParser().parse(jsonString).getAsJsonObject();
            if (root.has("schemaVersion")) {
                return REWARD_CONFIG_CODEC.decode(jsonString);
            }
            return REWARD_CONFIG_CODEC.toRuntimeConfig(
                    LEGACY_REWARD_CONFIG_READER.read(jsonString));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to deserialize reward configuration", e);
        }
    }

    public static Map<String, RewardList> getRewardMap(ERewardRule rule) {
        return getRewardMap(getRewardConfig(), rule);
    }

    public static Map<String, RewardList> getRewardMap(RewardConfig data, ERewardRule rule) {
        Map<String, RewardList> result = new LinkedHashMap<>();
        switch (rule) {
            case BASE_REWARD:
                result.put("base", data.getBaseRewards());
                break;
            case CONTINUOUS_REWARD:
                result = data.getContinuousRewards();
                break;
            case CYCLE_REWARD:
                result = data.getCycleRewards();
                break;
            case YEAR_REWARD:
                result = data.getYearRewards();
                break;
            case MONTH_REWARD:
                result = data.getMonthRewards();
                break;
            case WEEK_REWARD:
                result = data.getWeekRewards();
                break;
            case DATE_TIME_REWARD:
                result = data.getDateTimeRewards();
                break;
            case CUMULATIVE_REWARD:
                result = data.getCumulativeRewards();
                break;
            case RANDOM_REWARD:
                result = randomRewardMap(data);
                break;
            case CDK_REWARD:
                result = new LinkedHashMap<>();
                for (int i = 0; i < data.getCdkRewards().size(); i++) {
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> keyValue = data.getCdkRewards().get(i);
                    // key | 过期时间 | 序号 | 数量
                    result.put(String.format("%s|%s|%d|%d", keyValue.key().key(), keyValue.key().value(), i, keyValue.value().value().get()), keyValue.value().key());
                }
                break;
            case PERSONAL_DATE_REWARD:
                for (PersonalDatePreset preset : data.getPersonalDatePresets()) {
                    result.put(preset.getId(), preset.getRewards());
                }
                break;
            case LOTTERY_REWARD:
                for (LotteryPool pool : data.getLotteryPools()) {
                    result.put(pool.getId(), pool.getRewards());
                }
                break;
        }
        return result;
    }

    public static void setRewardMap(RewardConfig data, ERewardRule rule, Map<String, RewardList> map) {
        switch (rule) {
            case BASE_REWARD:
                data.setBaseRewards(map.getOrDefault("base", new RewardList()));
                break;
            case CONTINUOUS_REWARD:
                data.setContinuousRewards(map);
                break;
            case CYCLE_REWARD:
                data.setCycleRewards(map);
                break;
            case YEAR_REWARD:
                data.setYearRewards(map);
                break;
            case MONTH_REWARD:
                data.setMonthRewards(map);
                break;
            case WEEK_REWARD:
                data.setWeekRewards(map);
                break;
            case DATE_TIME_REWARD:
                data.setDateTimeRewards(map);
                break;
            case CUMULATIVE_REWARD:
                data.setCumulativeRewards(map);
                break;
            case RANDOM_REWARD:
                data.getRandomRewardGroups().clear();
                map.forEach((key, rewards) -> data.addRandomRewardGroup(
                        randomProbability(key), rewards));
                break;
            case CDK_REWARD:
                List<KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>>> cdkRewards = new ArrayList<>();
                // key | 过期时间 | 序号 | 数量
                map.keySet().stream()
                        .filter(StringUtils::isNotNullOrEmpty)
                        .filter(s -> s.replaceAll("\\|", ",").split(",").length >= 3)
                        .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("\\|", ",").split(",")[2])))
                        .forEach(key -> {
                            String[] split = key.replaceAll("\\|", ",").split(",");
                            cdkRewards.add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(map.get(key), new AtomicInteger(NumberUtils.toInt(split[3], 1)))));
                        });
                data.setCdkRewards(cdkRewards);
                break;
            case PERSONAL_DATE_REWARD:
                data.getPersonalDatePresets().forEach(preset -> {
                    RewardList rewards = map.get(preset.getId());
                    if (rewards != null) {
                        preset.setRewards(rewards);
                    }
                });
                break;
            case LOTTERY_REWARD:
                data.getLotteryPools().forEach(pool -> {
                    RewardList rewards = map.get(pool.getId());
                    if (rewards != null) {
                        pool.setRewards(rewards);
                    }
                });
                break;
        }
    }

    /**
     * 获取奖励配置数据包
     *
     * @param player 玩家，用于判断是否有权限
     */
    public static RewardOptionSyncPacket toSyncPacket(PlayerEntity player) {
        return toSyncPacket(getRewardConfig(),
                rule -> player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rule)));
    }

    static RewardOptionSyncPacket toSyncPacket(RewardConfig config,
                                                Predicate<ERewardRule> canView) {
        List<RewardOptionSyncData> dataList = new ArrayList<>();
        for (ERewardRule rule : ERewardRule.values()) {
            // 如果对应查看权限不足则将数据置为空，并在服务端解析时不进行该数据的覆盖
            if (!canView.test(rule)) {
                dataList.add(RewardOptionSyncData.redactedRule(rule));
            } else if (rule == ERewardRule.PERSONAL_DATE_REWARD
                    || rule == ERewardRule.LOTTERY_REWARD) {
                dataList.add(RewardOptionSyncData.emptyGroup(rule, ""));
            } else {
                dataList.addAll(toSyncData(config, rule));
            }
        }
        return new RewardOptionSyncPacket(dataList);
    }

    /**
     * 空规则组也需要显式同步，否则会改变随机奖励的总权重。
     */
    public static List<RewardOptionSyncData> toSyncData(RewardConfig config, ERewardRule rule) {
        List<RewardOptionSyncData> result = new ArrayList<>();
        getRewardMap(config, rule).forEach((key, rewards) -> {
            if (rewards.isEmpty()) {
                result.add(RewardOptionSyncData.emptyGroup(rule, key));
            } else {
                rewards.forEach(reward ->
                        result.add(new RewardOptionSyncData(rule, key, reward)));
            }
        });
        return result;
    }

    public static RewardConfig fromSyncPacketList(List<RewardOptionSyncPacket> packetList) {
        RewardConfig result = new RewardConfig();
        packetList.stream().flatMap(packet -> packet.getRewardOptionData().stream())
                .collect(Collectors.groupingBy(RewardOptionSyncData::getRule))
                .forEach((rule, dataList) -> {
                    if (rule == ERewardRule.PERSONAL_DATE_REWARD
                            || rule == ERewardRule.LOTTERY_REWARD) {
                        return;
                    }
                    Map<String, RewardList> rewardMap = new LinkedHashMap<>();
                    for (RewardOptionSyncData data : dataList) {
                        RewardList rewardList = rewardMap.computeIfAbsent(data.getKey(), key -> new RewardList());
                        if (data.getKind() == RewardOptionSyncKind.REWARD) {
                            rewardList.add(data.getReward());
                        }
                    }
                    // 如果当前为服务器环境，且玩家发送的数据为空则使用原数据，不进行覆盖
                    boolean redacted = dataList.stream()
                            .anyMatch(data -> data.getKind() == RewardOptionSyncKind.REDACTED_RULE);
                    if (redacted && BaniraEnvironment.isDedicatedServer()) {
                        rewardMap = RewardConfigManager.getRewardMap(rule);
                    }
                    if (redacted) {
                        rewardMap.remove("");
                    }
                    RewardConfigManager.setRewardMap(result, rule, rewardMap);
                });
        return result;
    }

    private static PersonalDatePreset personalDatePreset(String id) {
        return getRewardConfig().getPersonalDatePresets().stream()
                .filter(preset -> preset != null && Objects.equals(id, preset.getId()))
                .findFirst().orElse(null);
    }

    public static LotteryPool lotteryPool(String id) {
        return getRewardConfig().getLotteryPools().stream()
                .filter(pool -> pool != null && Objects.equals(id, pool.getId()))
                .findFirst().orElse(null);
    }

    @NonNull
    public static String getCdkRewardKey(String key) {
        String result = "";
        if (StringUtils.isNotNullOrEmpty(key)) {
            result = key.replaceAll("\\|", ",").split(",")[0];
        }
        return result;
    }

    @NonNull
    public static String getCdkRewardDate(String key) {
        String date = "";
        if (StringUtils.isNotNullOrEmpty(key)) {
            String[] split = key.replaceAll("\\|", ",").split(",");
            if (split.length == 3 || split.length == 4) {
                date = split[1];
            }
        }
        return date;
    }

    public static int getCdkRewardNum(String key) {
        String num = "";
        if (StringUtils.isNotNullOrEmpty(key)) {
            String[] split = key.replaceAll("\\|", ",").split(",");
            if (split.length == 4) {
                num = split[3];
            }
        }
        return NumberUtils.toInt(num, 1);
    }

    public static int getCdkRewardIndex(String key) {
        int index = 0;
        if (StringUtils.isNotNullOrEmpty(key)) {
            String[] split = key.replaceAll("\\|", ",").split(",");
            index = getRewardConfig().getCdkRewards().size();
            if (split.length == 3) {
                index = NumberUtils.toInt(split[2]);
            }
        }
        return index;
    }

}
