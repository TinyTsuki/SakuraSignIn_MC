package xin.vanilla.sakura.rewards;

import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.StringUtils;

import static xin.vanilla.sakura.config.RewardConfigManager.GSON;

@OnlyIn(Dist.CLIENT)
public class RewardClipboardManager {

    private static String lastClipboard = "";
    private static RewardClipboardList lastClipboardList = new RewardClipboardList();

    public static RewardClipboardList toClipboardList(RewardList rewardList, String key) {
        RewardClipboardList clipboardList = GSON.fromJson(rewardList.toJsonArray(), new TypeToken<RewardClipboardList>() {
        }.getType());
        if (CollectionUtils.isNotNullOrEmpty(clipboardList)) {
            clipboardList.forEach(reward -> reward.setKey(key));
        }
        return clipboardList;
    }

    public static RewardClipboard toClipboard(Reward reward, String key) {
        RewardClipboard clipboard = GSON.fromJson(reward.toJsonObject(), new TypeToken<RewardClipboard>() {
        }.getType());
        clipboard.setKey(key);
        return clipboard;
    }

    /**
     * 反序列化 RewardClipboardList
     */
    @NonNull
    public static RewardClipboardList deSerializeRewardList() {
        return deSerializeRewardList(getClipboard());
    }

    /**
     * 反序列化 RewardClipboardList
     */
    @NonNull
    public static RewardClipboardList deSerializeRewardList(String jsonString) {
        RewardClipboardList rewardList = new RewardClipboardList();
        if (StringUtils.isNotNullOrEmpty(jsonString)) {
            if (jsonString.startsWith("[")) {
                RewardClipboardList list = GSON.fromJson(jsonString, new TypeToken<RewardClipboardList>() {
                }.getType());
                rewardList.addAll(list);
            } else if (jsonString.startsWith("{")) {
                RewardClipboard reward = GSON.fromJson(jsonString, new TypeToken<RewardClipboard>() {
                }.getType());
                rewardList.add(reward);
            }
        }
        rewardList.removeIf(reward -> reward == null || reward.getContent() == null || reward.getType() == null);
        return rewardList;
    }

    /**
     * 序列化 RewardClipboardList
     */
    @NonNull
    public static String serialize(RewardClipboardList rewardList) {
        return GSON.toJson(rewardList.toJsonArray());
    }

    /**
     * 序列化 RewardClipboard
     */
    @NonNull
    public static String serialize(RewardClipboard reward) {
        return GSON.toJson(reward.toJsonObject());
    }

    /**
     * 剪贴板内容格式是否有效
     */
    public static boolean isClipboardValid() {
        getClipboard();
        return !lastClipboardList.isEmpty();
    }

    /**
     * 获取剪贴板内容
     */
    public static String getClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard.equals(lastClipboard)) {
            return lastClipboard;
        }
        lastClipboard = clipboard;
        lastClipboardList = deSerializeRewardList(clipboard);
        return clipboard;
    }

    /**
     * 设置剪贴板内容
     */
    public static void setClipboard(RewardList rewardList, String key) {
        setClipboard(serialize(toClipboardList(rewardList, key)));
    }

    /**
     * 设置剪贴板内容
     */
    public static void setClipboard(Reward reward, String key) {
        setClipboard(serialize(toClipboard(reward, key)));
    }

    /**
     * 设置剪贴板内容
     */
    public static void setClipboard(RewardClipboardList rewardList) {
        setClipboard(serialize(rewardList));
    }

    /**
     * 设置剪贴板内容
     */
    public static void setClipboard(RewardClipboard reward) {
        setClipboard(serialize(reward));
    }

    /**
     * 设置剪贴板内容
     */
    public static void setClipboard(String string) {
        Minecraft.getInstance().keyboardHandler.setClipboard(string);
    }
}
