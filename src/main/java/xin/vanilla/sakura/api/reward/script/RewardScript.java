package xin.vanilla.sakura.api.reward.script;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardTypeDefinition;

/**
 * Trusted, server-local Java code. Implementations must have a public no-arg constructor.
 */
public interface RewardScript {
    RewardTypeDefinition<JsonObject> definition();

    ScriptRewardDescriptor descriptor();
}
