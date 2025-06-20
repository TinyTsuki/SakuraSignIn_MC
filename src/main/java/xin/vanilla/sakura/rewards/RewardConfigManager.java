package xin.vanilla.sakura.rewards;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.RewardConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.enums.EnumRewardRule;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.network.packet.RewardOptionSyncToBoth;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.sakura.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RewardConfigManager {
    public static final Gson GSON = new GsonBuilder().enableComplexMapKeySerialization().create();

    public static final String FILE_NAME = "reward_option_data.json";

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
        AtomicReference<String> ip = new AtomicReference<>("");
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ip.set(SakuraUtils.getServerIP()));
        Path path = SakuraUtils.getConfigPath();
        if (StringUtils.isNotNullOrEmpty(ip.get())) path = path.resolve(ip.get());
        return path;
    }

    /**
     * 获取服务端配置文件路径
     */
    public static Path getServerConfigDirectory() {
        return new File(SakuraSignIn.getServerInstance().getServerDirectory(), "serverconfig" + File.separator + SakuraSignIn.MODID).toPath();
    }

    /**
     * 加载 JSON 数据
     */
    public static void loadRewardOption(boolean init) {
        {
            File file = new File(RewardConfigManager.getConfigDirectory().toFile(), FILE_NAME);
            if (file.exists()) {
                try {
                    rewardConfig = RewardConfigManager.deserializeRewardOption(new String(Files.readAllBytes(Paths.get(file.getPath()))));
                } catch (Exception e) {
                    LOGGER.error("Error loading sign-in data: ", e);
                }
            } else {
                if (init) {
                    // 初始化默认值
                    rewardConfig = RewardConfig.getDefault();
                } else {
                    rewardConfig = new RewardConfig();
                }
                RewardConfigManager.saveRewardOption();
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

    private static void deserializeHistoryFile(File redoHistoryFile, Map<EnumRewardRule, ConcurrentLinkedDeque<String>> redoList) throws IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(redoHistoryFile.getPath())));
        JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
        for (EnumRewardRule rule : EnumRewardRule.values()) {
            JsonArray jsonArray = jsonObject.getAsJsonArray(rule.name().toLowerCase());
            if (jsonArray != null) {
                for (JsonElement jsonElement : jsonArray) {
                    redoList.computeIfAbsent(rule, k -> new ConcurrentLinkedDeque<>()).add(jsonElement.getAsString());
                }
            }
        }
    }

    private static void serializeHistoryFile(File historyFile, Map<EnumRewardRule, ConcurrentLinkedDeque<String>> list) {
        JsonObject jsonObject = new JsonObject();
        for (EnumRewardRule rule : EnumRewardRule.values()) {
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
            File dir = RewardConfigManager.getConfigDirectory().toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, FILE_NAME);
            try (FileWriter writer = new FileWriter(file)) {
                // 格式化输出
                Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
                writer.write(gson.toJson(rewardConfig.toJsonObject()));
            } catch (IOException e) {
                LOGGER.error("Error saving sign-in data: ", e);
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
    private static final Map<EnumRewardRule, ConcurrentLinkedDeque<String>> undoList = new ConcurrentHashMap<>();
    /**
     * 恢复列表
     */
    private static final Map<EnumRewardRule, ConcurrentLinkedDeque<String>> redoList = new ConcurrentHashMap<>();

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
    public static void addUndoRewardOption(EnumRewardRule rule) {
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
    public static Map<String, RewardList> getUnDoRewardOption(EnumRewardRule rule) {
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
    public static void addRedoRewardOption(EnumRewardRule rule) {
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
    public static Map<String, RewardList> getReDoRewardOption(EnumRewardRule rule) {
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
        // 备份文件
        long dateTimeInt = DateUtils.toDateTimeInt(new Date());
        File sourceFolder = RewardConfigManager.getConfigDirectory().toFile();
        File sourceFile = new File(sourceFolder, RewardConfigManager.FILE_NAME);
        if (sourceFile.exists()) {
            try {
                File target = new File(new File(sourceFolder, "backups"), String.format("%s_%s.%s", RewardConfigManager.FILE_NAME, dateTimeInt, "old"));
                if (target.getParent() != null && !Files.exists(target.getParentFile().toPath())) {
                    Files.createDirectories(target.getParentFile().toPath());
                }
                Files.move(sourceFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("Error moving file: ", e);
            }
            // 备份最新编辑的文件
            if (save) {
                RewardConfigManager.saveRewardOption();
                try {
                    File target = new File(new File(sourceFolder, "backups"), String.format("%s_%s.%s", RewardConfigManager.FILE_NAME, dateTimeInt, "bak"));
                    if (target.getParent() != null && !Files.exists(target.getParentFile().toPath())) {
                        Files.createDirectories(target.getParentFile().toPath());
                    }
                    Files.move(sourceFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.error("Error moving file: ", e);
                }
            }
            // 删除旧文件
            deleteOldFile(sourceFolder, 20);
        }
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
    public static boolean validateKeyName(@NonNull EnumRewardRule rule, @NonNull String keyName) {
        boolean result;
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD:
            case CYCLE_REWARD:
                result = StringUtils.toInt(keyName) > 0;
                break;
            case YEAR_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 366;
            }
            break;
            case MONTH_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 31;
            }
            break;
            case WEEK_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 7;
            }
            break;
            case DATE_TIME_REWARD:
                result = !RewardConfig.parseDateRange(keyName).isEmpty();
                break;
            case CUMULATIVE_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0;
            }
            break;
            case RANDOM_REWARD: {
                BigDecimal property = StringUtils.toBigDecimal(keyName);
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
    public static RewardList getKeyName(@NonNull EnumRewardRule rule, @NonNull String keyName) {
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
                result = rewardConfig.getRandomRewards().get(keyName);
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int key = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    key = StringUtils.toInt(split[2]);
                }
                if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                    result = null;
                } else {
                    result = rewardConfig.getCdkRewards().get(key).getValue().getKey();
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
    public static void addKeyName(@NonNull EnumRewardRule rule, @NonNull String keyName, @NonNull RewardList rewardList) {
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
                rewardConfig.addRandomReward(keyName, rewardList);
                break;
            case CDK_REWARD:
                String date = getCdkRewardDate(keyName);
                int index = getCdkRewardIndex(keyName);
                if (rewardConfig.getCdkRewards().size() <= index || index < 0) {
                    rewardConfig.addCdkReward(new KeyValue<>(new KeyValue<>(getCdkRewardKey(keyName), date), new KeyValue<>(rewardList, new AtomicInteger(getCdkRewardNum(keyName)))));
                } else {
                    rewardConfig.getCdkRewards().get(index).getValue().getKey().addAll(rewardList);
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
    public static void updateKeyName(@NonNull EnumRewardRule rule, @NonNull String oldKeyName, @NonNull String newKeyName) {
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
                RewardList remove = rewardConfig.getRandomRewards().remove(oldKeyName);
                rewardConfig.addRandomReward(newKeyName, remove);
            }
            break;
            case CDK_REWARD: {
                String[] oldSplit = oldKeyName.replaceAll("\\|", ",").split(",");
                int oldIndex = rewardConfig.getCdkRewards().size();
                if (oldSplit.length == 3 || oldSplit.length == 4) {
                    oldIndex = StringUtils.toInt(oldSplit[2]);
                }
                if (rewardConfig.getCdkRewards().size() > oldIndex) {
                    String[] split = newKeyName.replaceAll("\\|", ",").split(",");
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> remove = rewardConfig.getCdkRewards().remove(oldIndex);
                    remove.getKey().setKey(split[0]);
                    remove.getKey().setValue(split[1]);
                    remove.getValue().getValue().set(StringUtils.toInt(split[3], 1));
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
    public static void clearKey(@NonNull EnumRewardRule rule, @NonNull String keyName) {
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
                rewardConfig.getRandomRewards().get(keyName).clear();
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = StringUtils.toInt(split[2]);
                }
                if (rewardConfig.getCdkRewards().size() > index) {
                    rewardConfig.getCdkRewards().get(index).getValue().getKey().clear();
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
    public static void deleteKey(@NonNull EnumRewardRule rule, @NonNull String keyName) {
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
                rewardConfig.getRandomRewards().remove(keyName);
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                int index = rewardConfig.getCdkRewards().size();
                if (split.length == 3 || split.length == 4) {
                    index = StringUtils.toInt(split[2]);
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
    public static Reward getReward(EnumRewardRule rule, String keyName, int index) {
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
                    result = rewardConfig.getRandomRewards().get(keyName).get(index);
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = StringUtils.toInt(split[2]);
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        result = null;
                    } else {
                        result = rewardConfig.getCdkRewards().get(key).getValue().getKey().get(index);
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
    public static void addReward(EnumRewardRule rule, String keyName, Reward reward) {
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
                if (!rewardConfig.getRandomRewards().containsKey(keyName)) {
                    rewardConfig.getRandomRewards().put(keyName, new RewardList());
                }
                rewardConfig.getRandomRewards().get(keyName).add(reward);
                break;
            case CDK_REWARD:
                String[] split = keyName.replaceAll("\\|", ",").split(",");
                String date = "";
                int key = rewardConfig.getCdkRewards().size();
                int num = 1;
                if (split.length == 3 || split.length == 4) {
                    date = split[1];
                    key = StringUtils.toInt(split[2]);
                    if (split.length == 4) {
                        num = StringUtils.toInt(split[3], 1);
                    }
                }
                if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                    rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], date), new KeyValue<>(new RewardList() {{
                        add(reward);
                    }}, new AtomicInteger(num))));
                } else {
                    rewardConfig.getCdkRewards().get(key).getValue().getKey().add(reward);
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
    public static void updateReward(EnumRewardRule rule, String keyName, int index, Reward reward) {
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
                    if (!rewardConfig.getRandomRewards().containsKey(keyName)) {
                        rewardConfig.getRandomRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardConfig.getRandomRewards().get(keyName).set(index, reward);
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    int num = 1;
                    if (split.length == 3 || split.length == 4) {
                        key = StringUtils.toInt(split[2]);
                        if (split.length == 4) {
                            num = StringUtils.toInt(split[3], 1);
                        }
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList() {{
                            add(reward);
                        }}, new AtomicInteger(num))));
                    } else {
                        rewardConfig.getCdkRewards().get(key).getValue().getKey().set(index, reward);
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
    public static void deleteReward(EnumRewardRule rule, String keyName, int index) {
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
                    rewardConfig.getRandomRewards().get(keyName).remove(index);
                    break;
                case CDK_REWARD:
                    String[] split = keyName.replaceAll("\\|", ",").split(",");
                    int key = rewardConfig.getCdkRewards().size();
                    if (split.length == 3 || split.length == 4) {
                        key = StringUtils.toInt(split[2]);
                    }
                    if (rewardConfig.getCdkRewards().size() <= key || key < 0) {
                        rewardConfig.getCdkRewards().add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(new RewardList(), new AtomicInteger(1))));
                    } else {
                        rewardConfig.getCdkRewards().get(key).getValue().getKey().remove(index);
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
    public static void sortRewards(EnumRewardRule rule) {
        List<EnumRewardRule> rules;
        if (rule == null) {
            rules = Arrays.asList(EnumRewardRule.values());
        } else {
            rules = Collections.singletonList(rule);
        }
        for (EnumRewardRule rewardRule : rules) {
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
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardConfig.getRandomRewards());
                    break;
                case CDK_REWARD:
                    rewardConfig.getCdkRewards().sort(Comparator.comparing(keyVal -> keyVal.getKey().getKey()));
                    break;
            }
        }
    }

    /**
     * 序列化 RewardOption
     */
    public static String serializeRewardOption(RewardConfig rewardConfig) {
        return GSON.toJson(rewardConfig.toJsonObject());
    }

    /**
     * 反序列化 RewardOption
     */
    @NonNull
    public static RewardConfig deserializeRewardOption(String jsonString) {
        RewardConfig result = new RewardConfig();
        if (StringUtils.isNotNullOrEmpty(jsonString)) {
            try {
                JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
                result.setBaseRewards(fromJsonOrDefault(jsonObject.get("baseRewards"), new TypeToken<RewardList>() {
                }.getType(), new RewardList()));
                result.setContinuousRewards(fromJsonOrDefault(jsonObject.get("continuousRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setCycleRewards(fromJsonOrDefault(jsonObject.get("cycleRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setYearRewards(fromJsonOrDefault(jsonObject.get("yearRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setMonthRewards(fromJsonOrDefault(jsonObject.get("monthRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setWeekRewards(fromJsonOrDefault(jsonObject.get("weekRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setDateTimeRewards(fromJsonOrDefault(jsonObject.get("dateTimeRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setCumulativeRewards(fromJsonOrDefault(jsonObject.get("cumulativeRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                result.setRandomRewards(fromJsonOrDefault(jsonObject.get("randomRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType(), new LinkedHashMap<>()));
                JsonArray cdkRewards = jsonObject.getAsJsonArray("cdkRewards");
                for (JsonElement cdkReward : cdkRewards) {
                    String key = ((JsonObject) cdkReward).get("key").getAsString();
                    String date = ((JsonObject) cdkReward).get("date").getAsString();
                    int num = 1;
                    try {
                        num = ((JsonObject) cdkReward).get("num").getAsInt();
                    } catch (Exception ignored) {
                    }
                    RewardList rewardList = fromJsonOrDefault(((JsonObject) cdkReward).get("value"), new TypeToken<RewardList>() {
                    }.getType(), new RewardList());
                    result.addCdkReward(new KeyValue<>(new KeyValue<>(key, date), new KeyValue<>(rewardList, new AtomicInteger(num))));
                }
                result.refreshContinuousRewardsRelation();
                result.refreshCycleRewardsRelation();
            } catch (Exception e) {
                LOGGER.error("Error loading sign-in data: ", e);
            }
        } else {
            result = new RewardConfig();
            RewardConfigManager.saveRewardOption();
        }
        return result;
    }

    @NonNull
    public static <T> T fromJsonOrDefault(JsonElement json, Type typeOfT, T defaultValue) {
        T result = GSON.fromJson(json, typeOfT);
        return result == null ? defaultValue : result;
    }

    /**
     * 反序列化 RewardList
     */
    @NonNull
    public static RewardList deSerializeRewardList(String jsonString) {
        RewardList rewardList = new RewardList();
        if (StringUtils.isNotNullOrEmpty(jsonString)) {
            if (jsonString.startsWith("[")) {
                RewardList list = GSON.fromJson(jsonString, new TypeToken<RewardList>() {
                }.getType());
                rewardList.addAll(list);
            } else if (jsonString.startsWith("{")) {
                Reward reward = GSON.fromJson(jsonString, new TypeToken<Reward>() {
                }.getType());
                rewardList.add(reward);
            }
        }
        return rewardList;
    }

    public static Map<String, RewardList> getRewardMap(EnumRewardRule rule) {
        Map<String, RewardList> result = new LinkedHashMap<>();
        switch (rule) {
            case BASE_REWARD:
                result.put("base", rewardConfig.getBaseRewards());
                break;
            case CONTINUOUS_REWARD:
                result = rewardConfig.getContinuousRewards();
                break;
            case CYCLE_REWARD:
                result = rewardConfig.getCycleRewards();
                break;
            case YEAR_REWARD:
                result = rewardConfig.getYearRewards();
                break;
            case MONTH_REWARD:
                result = rewardConfig.getMonthRewards();
                break;
            case WEEK_REWARD:
                result = rewardConfig.getWeekRewards();
                break;
            case DATE_TIME_REWARD:
                result = rewardConfig.getDateTimeRewards();
                break;
            case CUMULATIVE_REWARD:
                result = rewardConfig.getCumulativeRewards();
                break;
            case RANDOM_REWARD:
                result = rewardConfig.getRandomRewards();
                break;
            case CDK_REWARD:
                result = new LinkedHashMap<>();
                for (int i = 0; i < rewardConfig.getCdkRewards().size(); i++) {
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> keyValue = rewardConfig.getCdkRewards().get(i);
                    // key | 过期时间 | 序号 | 数量
                    result.put(String.format("%s|%s|%d|%d", keyValue.getKey().getKey(), keyValue.getKey().getValue(), i, keyValue.getValue().getValue().get()), keyValue.getValue().getKey());
                }
                break;
        }
        return result;
    }

    public static void setRewardMap(RewardConfig data, EnumRewardRule rule, Map<String, RewardList> map) {
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
                data.setRandomRewards(map);
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
                            cdkRewards.add(new KeyValue<>(new KeyValue<>(split[0], split[1]), new KeyValue<>(map.get(key), new AtomicInteger(StringUtils.toInt(split[3], 1)))));
                        });
                data.setCdkRewards(cdkRewards);
        }
    }

    /**
     * 获取奖励配置数据包
     *
     * @param player 玩家，用于判断是否有权限
     */
    public static RewardOptionSyncToBoth toSyncPacket(PlayerEntity player) {
        List<RewardOptionSyncData> dataList = new ArrayList<>();
        for (EnumRewardRule rule : EnumRewardRule.values()) {
            // 如果对应查看权限不足则将数据置为空，并在服务端解析时不进行该数据的覆盖
            if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rule))) {
                dataList.add(new RewardOptionSyncData(rule, "", new Reward(0, EnumRewardType.SIGN_IN_CARD).setDisabled(true)));
            } else {
                RewardConfigManager.getRewardMap(rule).forEach((key, value) -> {
                    List<RewardOptionSyncData> list = value.stream()
                            .map(reward -> new RewardOptionSyncData(rule, key, reward))
                            .collect(Collectors.toList());
                    dataList.addAll(list);
                });
            }
        }
        return new RewardOptionSyncToBoth(dataList);
    }

    public static RewardConfig fromSyncPacketList(List<RewardOptionSyncToBoth> packetList) {
        RewardConfig result = new RewardConfig();
        packetList.stream().flatMap(packet -> packet.getRewardOptionData().stream())
                .collect(Collectors.groupingBy(RewardOptionSyncData::getRule))
                .forEach((rule, dataList) -> {
                    Map<String, RewardList> rewardMap = new LinkedHashMap<>();
                    for (RewardOptionSyncData data : dataList) {
                        RewardList rewardList = rewardMap.computeIfAbsent(data.getKey(), key -> new RewardList());
                        rewardList.add(data.getReward());
                    }
                    // 如果当前为服务器环境，且玩家发送的数据为空则使用原数据，不进行覆盖
                    if (FMLEnvironment.dist == Dist.DEDICATED_SERVER
                            && rewardMap.size() == 1
                            && rewardMap.containsKey("")
                            && rewardMap.get("").size() == 1
                            && rewardMap.get("").get(0).getType() == EnumRewardType.SIGN_IN_CARD
                            && rewardMap.get("").get(0).isDisabled()) {
                        rewardMap = RewardConfigManager.getRewardMap(rule);
                    }
                    rewardMap.keySet().removeIf(StringUtils::isNullOrEmpty);
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
        return StringUtils.toInt(num, 1);
    }

    public static int getCdkRewardIndex(String key) {
        int index = 0;
        if (StringUtils.isNotNullOrEmpty(key)) {
            String[] split = key.replaceAll("\\|", ",").split(",");
            index = rewardConfig.getCdkRewards().size();
            if (split.length == 3 || split.length == 4) {
                index = StringUtils.toInt(split[2]);
            }
        }
        return index;
    }

}
