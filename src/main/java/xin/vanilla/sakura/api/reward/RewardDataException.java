package xin.vanilla.sakura.api.reward;

/**
 * 奖励内容无法被可靠解码或编码。
 */
public class RewardDataException extends Exception {
    public RewardDataException(String message) {
        super(message);
    }

    public RewardDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
