package xin.vanilla.sakura.api.reward;

import net.minecraft.server.level.ServerPlayer;

import java.util.Date;
import java.util.UUID;

/**
 * 奖励执行器可使用的受控服务端上下文。
 */
public interface RewardGrantContext {

    ServerPlayer player();

    UUID playerId();

    Date signInDate();

    String sourceId();

    void addSignInCards(int amount);
}
