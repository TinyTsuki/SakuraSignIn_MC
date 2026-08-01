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

public class RewardConfigManager {
    public static final Gson GSON = new GsonBuilder().enableComplexMapKeySerialization().create();

    public static final String FILE_NAME = RewardConfigRepository.REWARD_FILE_NAME;
    private static final String RANDOM_GROUP_SEPARATOR = "#";
    private static final RewardConfigCodec REWARD_CONFIG_CODEC = new RewardConfigCodec();
    private static final LegacyRewardConfigReader LEGACY_REWARD_CONFIG_READER =
            new LegacyRewardConfigReader();

    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    @Setter
    @NonNull
    private static RewardConfig rewardConfig = new RewardConfig();
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
        return BaniraDataPaths.gameConfigPath().resolve(SakuraSignIn.MODID);
    }

    /**
     * 加载 JSON 数据
     */
    public static void loadRewardOption() {
        {
            try {
                rewardConfig = repository().loadOrCreate(RewardConfig.getDefault());
                rewardConfig.refreshContinuousRewardsRelation();
                rewardConfig.refreshCycleRewardsRelation();
            } catch (Exception e) {
                LOGGER.error("Error loading reward configuration: ", e);
            }
        }
        {
            File undoHistoryFile = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/undo_history.json");
            if (undoHistoryFile.exists()) {
                try {
                    deserializeHistoryFile(undoHistoryFile, undoList);
                } catch (Exception e) {
                    LOGGER.error("Error loading undo history: ", e);
                }
            }
            File redoHistoryFile = new File(RewardConfigManager.getConfigDirectory().toFile(), "history/redo_history.json");
            if (redoHistoryFile.exists()) {
                try {
                    deserializeHistoryFile(redoHistoryFile, redoList);
                } catch (Exception e) {
                    LOGGER.error("Error loading redo history: ", e);
                }
            }
        }
    }

    private static void deserializeHistoryFile(File redoHistoryFile, Map<ERewardRule, ConcurrentLinkedDeque<String>> redoList) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(redoHistoryFile.getPath())));
        JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
        for (ERewardRule rule : ERewardRule.values()) {
            JsonArray jsonArray = jsonObject.getAsJsonArray(rule.name().toLowerCase());
            if (jsonArray != null) {
                for (JsonElement jsonElement : jsonArray) {
                    redoList.computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).add(jsonElement.getAsString());
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
    public static void saveRewardOption() {
        {
            try {
                repository().save(rewardConfig);
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
            serializeHistoryFile(undoHistoryFile, undoList);
            File redoHistoryFile = new File(dir, "redo_history.json");
            serializeHistoryFile(redoHistoryFile, redoList);
        }
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

    public static void clearUndoList() {
        undoList.clear();
    }

    public static void clearRedoList() {
        redoList.clear();
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
            undoList.computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).push(historyId);
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
        File dataFile = new File(dir, undoList.getOrDefault(rule, new ConcurrentLinkedDeque<>()).poll() + "." + FILE_NAME);
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
            redoList.computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).push(historyId);
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
        File dataFile = new File(dir, redoList.getOrDefault(rule, new ConcurrentLinkedDeque<>()).poll() + "." + FILE_NAME);
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
                if (index >= 0 && index < rewardConfig.getRandomRewardGroups().size()
                        && rewardConfig.getRandomRewardGroups().get(index).getKey()
                        .equals(key.substring(0, separator))) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String probability = randomProbability(key);
        for (int i = 0; i < rewardConfig.getRandomRewardGroups().size(); i++) {
            if (rewardConfig.getRandomRewardGroups().get(i).getKey().equals(probability)) {
                return i;
            }
        }
        return -1;
    }

    private static RewardGroup randomGroup(String key) {
        int index = randomGroupIndex(key);
        return index < 0 ? null : rewardConfig.getRandomRewardGroups().get(index);
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
                result = rewardConfig.getBaseRewards();
                break;
            case CONTINUOUS_REWARD:
                result = rewardConfig.getContinuousRewards().get(keyName);
                break;
            case CYCLE_REWARD:
                result = rewardConfig.getCycleRewards().get(keyName);
                break;
            case YEAR_REWARD:
                result = rewardConfig.getYearRewards().get(keyName);
                break;
            case MONTH_REWARD:
                result = rewardConfig.getMonthRewards().get(keyName);
                break;
            case WEEK_REWARD:
                result = rewardConfig.getWeekRewards().get(keyName);
                break;
            case DATE_TIME_REWARD:
                result = rewardConfig.getDateTimeRewards().get(keyName);
                break;
            case CUMULATIVE_REWARD:
                result = rewardConfig.getCumulativeRewards().get(keyName);
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                result = randomGroup == null ? null : randomGroup.getRewards();
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int key = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    key = NumberUtils.toInt(split[2]);
                }
                if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                    result = null;
                } else {
                    result = rewardConfig.getCdkRewards().get(key).value().key();
                }
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
                rewardConfig.getBaseRewards().addAll(rewardList);
                break;
            case CONTINUOUS_REWARD:
                rewardConfig.addContinuousRewards(keyName, rewardList);
                break;
            case CYCLE_REWARD:
                rewardConfig.addCycleRewards(keyName, rewardList);
                break;
            case YEAR_REWARD:
                rewardConfig.addYearRewards(keyName, rewardList);
                break;
            case MONTH_REWARD:
                rewardConfig.addMonthRewards(keyName, rewardList);
                break;
            case WEEK_REWARD:
                rewardConfig.addWeekRewards(keyName, rewardList);
                break;
            case DATE_TIME_REWARD:
                rewardConfig.addDateTimeRewards(keyName, rewardList);
                break;
            case CUMULATIVE_REWARD:
                rewardConfig.addCumulativeReward(keyName, rewardList);
                break;
            case RANDOM_REWARD:
                RewardGroup existingRandomGroup = keyName.contains(RANDOM_GROUP_SEPARATOR)
                        ? randomGroup(keyName)
                        : null;
                if (existingRandomGroup == null) {
                    rewardConfig.addRandomRewardGroup(randomProbability(keyName), rewardList);
                } else {
                    existingRandomGroup.getRewards().addAll(rewardList);
                }
                break;
            case CDK_REWARD:
                String date = getCdkRewardDate(keyName);
                int index = getCdkRewardIndex(keyName);
                if (rewardConfig.getCdkRewards().size() <= index || index < 0) {
                    rewardConfig.addCdkReward(new KeyValue<>(new KeyValue<>(getCdkRewardKey(keyName), date), new KeyValue<>(rewardList, new AtomicInteger(getCdkRewardNum(keyName)))));
                } else {
                    rewardConfig.getCdkRewards().get(index).value().key().addAll(rewardList);
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
                RewardList remove = rewardConfig.getContinuousRewards().remove(oldKeyName);
                rewardConfig.addContinuousRewards(newKeyName, remove);
            }
            break;
            case CYCLE_REWARD: {
                RewardList remove = rewardConfig.getCycleRewards().remove(oldKeyName);
                rewardConfig.addCycleRewards(newKeyName, remove);
            }
            break;
            case YEAR_REWARD: {
                RewardList remove = rewardConfig.getYearRewards().remove(oldKeyName);
                rewardConfig.addYearRewards(newKeyName, remove);
            }
            break;
            case MONTH_REWARD: {
                RewardList remove = rewardConfig.getMonthRewards().remove(oldKeyName);
                rewardConfig.addMonthRewards(newKeyName, remove);
            }
            break;
            case WEEK_REWARD: {
                RewardList remove = rewardConfig.getWeekRewards().remove(oldKeyName);
                rewardConfig.addWeekRewards(newKeyName, remove);
            }
            break;
            case DATE_TIME_REWARD: {
                RewardList remove = rewardConfig.getDateTimeRewards().remove(oldKeyName);
                rewardConfig.addDateTimeRewards(newKeyName, remove);
            }
            break;
            case CUMULATIVE_REWARD: {
                RewardList remove = rewardConfig.getCumulativeRewards().remove(oldKeyName);
                rewardConfig.addCumulativeReward(newKeyName, remove);
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
                int oldIndex = rewardConfig.getCdkRewards().size();
                if (oldSplit.length == 3 || oldSplit.length == 4) {
                    oldIndex = NumberUtils.toInt(oldSplit[2]);
                }
                if (rewardConfig.getCdkRewards().size() > oldIndex) {
                    String[] split = newKeyName.replaceAll("\\|", ",").split(",");
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> remove = rewardConfig.getCdkRewards().remove(oldIndex);
                    remove.key().key(split[0]);
                    remove.key().value(split[1]);
                    remove.value().value().set(NumberUtils.toInt(split[3], 1));
                    rewardConfig.addCdkReward(remove);
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
                rewardConfig.getBaseRewards().clear();
                break;
            case CONTINUOUS_REWARD:
                rewardConfig.getContinuousRewards().get(keyName).clear();
                break;
            case CYCLE_REWARD:
                rewardConfig.getCycleRewards().get(keyName).clear();
                break;
            case YEAR_REWARD:
                rewardConfig.getYearRewards().get(keyName).clear();
                break;
            case MONTH_REWARD:
                rewardConfig.getMonthRewards().get(keyName).clear();
                break;
            case WEEK_REWARD:
                rewardConfig.getWeekRewards().get(keyName).clear();
                break;
            case DATE_TIME_REWARD:
                rewardConfig.getDateTimeRewards().get(keyName).clear();
                break;
            case CUMULATIVE_REWARD:
                rewardConfig.getCumulativeRewards().get(keyName).clear();
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                if (randomGroup != null) {
                    randomGroup.getRewards().clear();
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = NumberUtils.toInt(split[2]);
                }
                if (rewardConfig.getCdkRewards().size() > index) {
                    rewardConfig.getCdkRewards().get(index).value().key().clear();
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
                rewardConfig.getContinuousRewards().remove(keyName);
                break;
            case CYCLE_REWARD:
                rewardConfig.getCycleRewards().remove(keyName);
                break;
            case YEAR_REWARD:
                rewardConfig.getYearRewards().remove(keyName);
                break;
            case MONTH_REWARD:
                rewardConfig.getMonthRewards().remove(keyName);
                break;
            case WEEK_REWARD:
                rewardConfig.getWeekRewards().remove(keyName);
                break;
            case DATE_TIME_REWARD:
                rewardConfig.getDateTimeRewards().remove(keyName);
                break;
            case CUMULATIVE_REWARD:
                rewardConfig.getCumulativeRewards().remove(keyName);
                break;
            case RANDOM_REWARD:
                int randomIndex = randomGroupIndex(keyName);
                if (randomIndex >= 0) {
                    rewardConfig.getRandomRewardGroups().remove(randomIndex);
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = NumberUtils.toInt(split[2]);
                }
                if (rewardConfig.getCdkRewards().size() > index) {
                    rewardConfig.getCdkRewards().remove(index);
                }
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
                    result = rewardConfig.getBaseRewards().get(index);
                    break;
                case CONTINUOUS_REWARD:
                    result = rewardConfig.getContinuousRewards().get(keyName).get(index);
                    break;
                case CYCLE_REWARD:
                    result = rewardConfig.getCycleRewards().get(keyName).get(index);
                    break;
                case YEAR_REWARD:
                    result = rewardConfig.getYearRewards().get(keyName).get(index);
                    break;
                case MONTH_REWARD:
                    result = rewardConfig.getMonthRewards().get(keyName).get(index);
                    break;
                case WEEK_REWARD:
                    result = rewardConfig.getWeekRewards().get(keyName).get(index);
                    break;
                case DATE_TIME_REWARD:
                    result = rewardConfig.getDateTimeRewards().get(keyName).get(index);
                    break;
                case CUMULATIVE_REWARD:
                    result = rewardConfig.getCumulativeRewards().get(keyName).get(index);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    result = randomGroup == null ? null : randomGroup.getRewards().get(index);
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        result = null;
                    } else {
                        result = rewardConfig.getCdkRewards().get(key).value().key().get(index);
                    }
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
                rewardConfig.getBaseRewards().add(reward);
                break;
            case CONTINUOUS_REWARD:
                if (!rewardConfig.getContinuousRewards().containsKey(keyName)) {
                    rewardConfig.getContinuousRewards().put(keyName, new RewardList());
                }
                rewardConfig.getContinuousRewards().get(keyName).add(reward);
                break;
            case CYCLE_REWARD:
                if (!rewardConfig.getCycleRewards().containsKey(keyName)) {
                    rewardConfig.getCycleRewards().put(keyName, new RewardList());
                }
                rewardConfig.getCycleRewards().get(keyName).add(reward);
                break;
            case YEAR_REWARD:
                if (!rewardConfig.getYearRewards().containsKey(keyName)) {
                    rewardConfig.getYearRewards().put(keyName, new RewardList());
                }
                rewardConfig.getYearRewards().get(keyName).add(reward);
                break;
            case MONTH_REWARD:
                if (!rewardConfig.getMonthRewards().containsKey(keyName)) {
                    rewardConfig.getMonthRewards().put(keyName, new RewardList());
                }
                rewardConfig.getMonthRewards().get(keyName).add(reward);
                break;
            case WEEK_REWARD:
                if (!rewardConfig.getWeekRewards().containsKey(keyName)) {
                    rewardConfig.getWeekRewards().put(keyName, new RewardList());
                }
                rewardConfig.getWeekRewards().get(keyName).add(reward);
                break;
            case DATE_TIME_REWARD:
                if (!rewardConfig.getDateTimeRewards().containsKey(keyName)) {
                    rewardConfig.getDateTimeRewards().put(keyName, new RewardList());
                }
                rewardConfig.getDateTimeRewards().get(keyName).add(reward);
                break;
            case CUMULATIVE_REWARD:
                if (!rewardConfig.getCumulativeRewards().containsKey(keyName)) {
                    rewardConfig.getCumulativeRewards().put(keyName, new RewardList());
                }
                rewardConfig.getCumulativeRewards().get(keyName).add(reward);
                break;
            case RANDOM_REWARD:
                RewardGroup randomGroup = randomGroup(keyName);
                if (randomGroup == null) {
                    rewardConfig.addRandomRewardGroup(
                            randomProbability(keyName),
                            new RewardList(Collections.singletonList(reward)));
                } else {
                    randomGroup.getRewards().add(reward);
                }
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                String date = "";
                int key = rewardConfig.getCdkRewards().size();
                int num = 1;
                if (split.length == 3 || split.length == 4) {
                    date = split[1];
                    key = NumberUtils.toInt(split[2]);
                    if (split.length == 4) {
                        num = NumberUtils.toInt(split[3], 1);
                    }
                }
                if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                    rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], date), new KeyValue<>(new RewardList() {{
                        add(reward);
                    }}, new AtomicInteger(num))));
                } else {
                    rewardConfig.getCdkRewards().get(key).value().key().add(reward);
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
                    rewardConfig.getBaseRewards().set(index, reward);
                    break;
                case CONTINUOUS_REWARD:
                    if (!rewardConfig.getContinuousRewards().containsKey(keyName)) {
                        rewardConfig.getContinuousRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getContinuousRewards().get(keyName).set(index, reward);
                    break;
                case CYCLE_REWARD:
                    if (!rewardConfig.getCycleRewards().containsKey(keyName)) {
                        rewardConfig.getCycleRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getCycleRewards().get(keyName).set(index, reward);
                    break;
                case YEAR_REWARD:
                    if (!rewardConfig.getYearRewards().containsKey(keyName)) {
                        rewardConfig.getYearRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getYearRewards().get(keyName).set(index, reward);
                    break;
                case MONTH_REWARD:
                    if (!rewardConfig.getMonthRewards().containsKey(keyName)) {
                        rewardConfig.getMonthRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getMonthRewards().get(keyName).set(index, reward);
                    break;
                case WEEK_REWARD:
                    if (!rewardConfig.getWeekRewards().containsKey(keyName)) {
                        rewardConfig.getWeekRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getWeekRewards().get(keyName).set(index, reward);
                    break;
                case DATE_TIME_REWARD:
                    if (!rewardConfig.getDateTimeRewards().containsKey(keyName)) {
                        rewardConfig.getDateTimeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getDateTimeRewards().get(keyName).set(index, reward);
                    break;
                case CUMULATIVE_REWARD:
                    if (!rewardConfig.getCumulativeRewards().containsKey(keyName)) {
                        rewardConfig.getCumulativeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getCumulativeRewards().get(keyName).set(index, reward);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    if (randomGroup == null) {
                        rewardConfig.addRandomRewardGroup(
                                randomProbability(keyName),
                                new RewardList(Collections.singletonList(reward)));
                    } else {
                        randomGroup.getRewards().set(index, reward);
                    }
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    int num = 1;
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                        if (split.length == 4) {
                            num = NumberUtils.toInt(split[3], 1);
                        }
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList() {{
                            add(reward);
                        }}, new AtomicInteger(num))));
                    } else {
                        rewardConfig.getCdkRewards().get(key).value().key().set(index, reward);
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
                    rewardConfig.getBaseRewards().remove(index);
                    break;
                case CONTINUOUS_REWARD:
                    rewardConfig.getContinuousRewards().get(keyName).remove(index);
                    break;
                case CYCLE_REWARD:
                    rewardConfig.getCycleRewards().get(keyName).remove(index);
                    break;
                case YEAR_REWARD:
                    rewardConfig.getYearRewards().get(keyName).remove(index);
                    break;
                case MONTH_REWARD:
                    rewardConfig.getMonthRewards().get(keyName).remove(index);
                    break;
                case WEEK_REWARD:
                    rewardConfig.getWeekRewards().get(keyName).remove(index);
                    break;
                case DATE_TIME_REWARD:
                    rewardConfig.getDateTimeRewards().get(keyName).remove(index);
                    break;
                case CUMULATIVE_REWARD:
                    rewardConfig.getCumulativeRewards().get(keyName).remove(index);
                    break;
                case RANDOM_REWARD:
                    RewardGroup randomGroup = randomGroup(keyName);
                    if (randomGroup != null) {
                        randomGroup.getRewards().remove(index);
                    }
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = NumberUtils.toInt(split[2]);
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList(), new AtomicInteger(1))));
                    } else {
                        rewardConfig.getCdkRewards().get(key).value().key().remove(index);
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
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getContinuousRewards());
                    break;
                case CYCLE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getCycleRewards());
                    break;
                case YEAR_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getYearRewards());
                    break;
                case MONTH_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getMonthRewards());
                    break;
                case WEEK_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getWeekRewards());
                    break;
                case DATE_TIME_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getDateTimeRewards());
                    break;
                case CUMULATIVE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getCumulativeRewards());
                    break;
                case RANDOM_REWARD:
                    rewardConfig.getRandomRewardGroups().sort(
                            Comparator.comparing(RewardGroup::getKey, RewardConfigManager::keyComparator));
                    break;
                case CDK_REWARD:
                    rewardConfig.getCdkRewards().sort(Comparator.comparing(keyVal -> keyVal.key().key()));
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
        return getRewardMap(rewardConfig, rule);
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
        }
    }

    /**
     * 获取奖励配置数据包
     *
     * @param player 玩家，用于判断是否有权限
     */
    public static RewardOptionSyncPacket toSyncPacket(PlayerEntity player) {
        List<RewardOptionSyncData> dataList = new ArrayList<>();
        for (ERewardRule rule : ERewardRule.values()) {
            // 如果对应查看权限不足则将数据置为空，并在服务端解析时不进行该数据的覆盖
            if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rule))) {
                dataList.add(RewardOptionSyncData.redactedRule(rule));
            } else {
                dataList.addAll(toSyncData(rewardConfig, rule));
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
            index = rewardConfig.getCdkRewards().size();
            if (split.length == 3) {
                index = NumberUtils.toInt(split[2]);
            }
        }
        return index;
    }

}
