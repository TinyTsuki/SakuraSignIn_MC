package xin.vanilla.sakura.api.reward;

public final class SakuraRewardTypes {
    public static final RewardTypeId ITEM = id("item");
    public static final RewardTypeId EFFECT = id("effect");
    public static final RewardTypeId EXPERIENCE_POINT = id("experience_point");
    public static final RewardTypeId EXPERIENCE_LEVEL = id("experience_level");
    public static final RewardTypeId SIGN_IN_CARD = id("sign_in_card");
    public static final RewardTypeId ADVANCEMENT = id("advancement");
    public static final RewardTypeId MESSAGE = id("message");
    public static final RewardTypeId COMMAND = id("command");

    private SakuraRewardTypes() {
    }

    private static RewardTypeId id(String path) {
        return RewardTypeId.of("sakura_sign_in", path);
    }
}
