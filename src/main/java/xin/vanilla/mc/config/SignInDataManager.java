package xin.vanilla.mc.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.rewards.RewardList;
import xin.vanilla.mc.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

public class SignInDataManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String FILE_NAME = "sign_in_data.json";

    @Getter
    @Setter
    @NonNull
    private static SignInData signInData = new SignInData();

    /**
     * 获取配置文件路径
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID);
    }

    /**
     * 加载 JSON 数据
     */
    public static void loadSignInData() {
        File file = new File(SignInDataManager.getConfigDirectory().toFile(), FILE_NAME);
        if (file.exists()) {
            try {
                signInData = SignInDataManager.deserializeSignInData(new String(Files.readAllBytes(Paths.get(file.getPath()))));
            } catch (IOException e) {
                LOGGER.error("Error loading sign-in data: ", e);
            }
        } else {
            // 如果文件不存在，初始化默认值
            signInData = SignInData.getDefault();
            SignInDataManager.saveSignInData();
        }
    }

    /**
     * 保存 JSON 数据
     */
    public static void saveSignInData() {
        File dir = SignInDataManager.getConfigDirectory().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            // 格式化输出
            Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
            writer.write(gson.toJson(signInData.toJsonObject()));
        } catch (IOException e) {
            LOGGER.error("Error saving sign-in data: ", e);
        }
    }

    /**
     * 序列化 SignInData
     */
    public static String serializeSignInData(SignInData signInData) {
        return GSON.toJson(signInData.toJsonObject());
    }

    /**
     * 反序列化 SignInData
     */
    public static SignInData deserializeSignInData(String jsonString) {
        SignInData result = new SignInData();
        if (StringUtils.isNotNullOrEmpty(jsonString)) {
            try {
                JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
                result.setBaseRewards(GSON.fromJson(jsonObject.get("baseRewards"), new TypeToken<RewardList>() {
                }.getType()));
                result.setContinuousRewards(GSON.fromJson(jsonObject.get("continuousRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setCycleRewards(GSON.fromJson(jsonObject.get("cycleRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setYearRewards(GSON.fromJson(jsonObject.get("yearRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setMonthRewards(GSON.fromJson(jsonObject.get("monthRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setWeekRewards(GSON.fromJson(jsonObject.get("weekRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setDateTimeRewards(GSON.fromJson(jsonObject.get("dateTimeRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                LOGGER.error("Error loading sign-in data: ", e);
            }
        } else {
            // 如果文件不存在，初始化默认值
            result = SignInData.getDefault();
            SignInDataManager.saveSignInData();
        }
        return result;
    }
}
