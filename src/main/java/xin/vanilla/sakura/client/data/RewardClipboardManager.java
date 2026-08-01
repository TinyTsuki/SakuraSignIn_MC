package xin.vanilla.sakura.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.banira.common.util.StringUtils;

public class RewardClipboardManager {

    private static String lastClipboard = "";
    private static RewardClipboardList lastClipboardList = new RewardClipboardList();

    public static RewardClipboardList toClipboardList(RewardList rewardList, String key) {
        RewardClipboardList clipboardList = new RewardClipboardList();
        if (rewardList != null) {
            rewardList.stream().filter(java.util.Objects::nonNull)
                    .map(reward -> RewardClipboard.fromReward(reward, key))
                    .forEach(clipboardList::add);
        }
        return clipboardList;
    }

    public static RewardClipboard toClipboard(Reward reward, String key) {
        return RewardClipboard.fromReward(reward, key);
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
            try {
                JsonElement parsed = new JsonParser().parse(jsonString);
                if (parsed.isJsonArray()) {
                    JsonArray array = parsed.getAsJsonArray();
                    for (JsonElement element : array) {
                        if (element != null && element.isJsonObject()) {
                            rewardList.add(RewardClipboard.fromJson(element.getAsJsonObject()));
                        }
                    }
                } else if (parsed.isJsonObject()) {
                    rewardList.add(RewardClipboard.fromJson(parsed.getAsJsonObject()));
                }
            } catch (RuntimeException ignored) {
                rewardList.clear();
            }
        }
        rewardList.removeIf(reward -> reward == null || reward.getContent() == null
                || reward.getTypeId() == null);
        return rewardList;
    }

    /**
     * 序列化 RewardClipboardList
     */
    @NonNull
    public static String serialize(RewardClipboardList rewardList) {
        return rewardList.toJsonArray().toString();
    }

    /**
     * 序列化 RewardClipboard
     */
    @NonNull
    public static String serialize(RewardClipboard reward) {
        return reward.toJsonObject().toString();
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
