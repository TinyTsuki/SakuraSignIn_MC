package xin.vanilla.sakura.api.reward.script;

import com.google.gson.JsonObject;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.api.reward.*;

import java.util.List;

/**
 * javac owns the generic bridges; Janino scripts implement only concrete JSON methods.
 */
public abstract class JsonRewardScript implements RewardScript {
    public abstract RewardGrantResult grant(RewardGrantContext context, JsonObject value);

    public abstract List<RewardViolation> validate(JsonObject value);

    public abstract Component describe(String language, JsonObject value, boolean withAmount);

    public abstract RewardAddPermission permission();

    @Override
    public final RewardTypeDefinition<JsonObject> definition() {
        return RewardTypeDefinition.builder(descriptor().id(), new JsonRewardCodec(), this::grant)
                .validator(this::validate).describer(this::describe).addPermission(permission()).build();
    }
}
