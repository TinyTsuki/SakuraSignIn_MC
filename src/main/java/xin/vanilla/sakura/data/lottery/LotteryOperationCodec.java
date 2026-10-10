package xin.vanilla.sakura.data.lottery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xin.vanilla.banira.api.cost.CostPaymentPlan;
import xin.vanilla.banira.api.cost.ResourceCost;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned durable operations, independent of packet and client caches. */
public final class LotteryOperationCodec {
    public static final int MAX_BYTES = 8_388_608;

    public String encode(LotteryOperation operation) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("playerId", operation.getPlayerId().toString());
        LotteryDrawRequest draw = operation.getRequest();
        LotteryQuoteRequest request = draw.getRequest();
        root.addProperty("session", request.getSession().toString());
        root.addProperty("sequence", request.getRequestId());
        root.addProperty("generation", request.getGeneration());
        root.addProperty("poolId", request.getPoolId());
        root.addProperty("requestedCount", request.getCount());
        root.addProperty("quoteToken", draw.getQuoteToken());
        root.addProperty("count", operation.getCount());
        root.addProperty("seed", operation.getSeed());
        root.addProperty("createdAt", operation.getCreatedAt());
        root.addProperty("displayToken", operation.getDisplayToken());
        root.addProperty("stage", operation.getStage().name());
        root.addProperty("grantedCount", operation.getGrantedCount());
        root.addProperty("acknowledged", operation.isAcknowledged());
        root.addProperty("rollbackPending", operation.isRollbackPending());
        root.addProperty("zoneId", operation.getZoneId());
        LotteryDrawState quota = operation.getQuotaBefore();
        JsonObject before = new JsonObject();
        before.addProperty("poolId", quota.getPoolId()); before.addProperty("periodKey", quota.getPeriodKey());
        before.addProperty("periodDraws", quota.getPeriodDraws()); before.addProperty("totalDraws", quota.getTotalDraws());
        before.addProperty("lastDraw", quota.getLastDrawEpochMillis()); root.add("quotaBefore", before);
        root.add("pool", new JsonParser().parse(operation.poolSnapshot()));
        JsonArray rewards = new JsonArray();
        for (String reward : operation.rewardSnapshots()) rewards.add(new JsonParser().parse(reward));
        root.add("rewards", rewards);
        if (operation.getReceipt() != null) root.add("receipt", receipt(operation.getReceipt()));
        String json = root.toString();
        try { LotteryProtocolLimits.json(json, MAX_BYTES); }
        catch (RuntimeException invalid) { throw new IOException("Lottery operation exceeds limits", invalid); }
        return json;
    }

    public LotteryOperation decode(String json) throws IOException {
        try {
            LotteryProtocolLimits.json(json, MAX_BYTES);
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (integer(root, "schemaVersion") != 2) throw new IllegalArgumentException("Unknown operation schema");
            LotteryDrawRequest request = new LotteryDrawRequest(uuid(root, "session"), number(root, "sequence"),
                    number(root, "generation"), string(root, "poolId"), integer(root, "requestedCount"),
                    string(root, "quoteToken"));
            List<String> rewards = new ArrayList<>();
            JsonArray array = required(root, "rewards").getAsJsonArray();
            if (array.size() > 100) throw new IllegalArgumentException("Too many draw results");
            for (JsonElement reward : array) rewards.add(reward.toString());
            JsonObject before = required(root, "quotaBefore").getAsJsonObject();
            LotteryDrawState quota = new LotteryDrawState(string(before, "poolId"));
            quota.setPeriodKey(string(before, "periodKey")); quota.setPeriodDraws(integer(before, "periodDraws"));
            quota.setTotalDraws(integer(before, "totalDraws")); quota.setLastDrawEpochMillis(number(before, "lastDraw"));
            return new LotteryOperation(uuid(root, "playerId"), request, required(root, "pool").toString(),
                    integer(root, "count"), number(root, "seed"), number(root, "createdAt"),
                    string(root, "displayToken"), LotteryOperation.Stage.valueOf(string(root, "stage")),
                    integer(root, "grantedCount"), rewards,
                    root.has("receipt") ? receipt(root.getAsJsonObject("receipt")) : null,
                    quota, string(root, "zoneId"), bool(root, "acknowledged"), bool(root, "rollbackPending"));
        } catch (RuntimeException invalid) { throw new IOException("Invalid lottery operation", invalid); }
    }

    private static JsonObject receipt(CostPaymentPlan plan) {
        JsonObject object = new JsonObject();
        object.addProperty("units", plan.units());
        object.addProperty("confirmed", plan.hasConfirmedSnapshots());
        object.addProperty("uncertain", plan.recoveryFailure().isFailure());
        JsonArray costs = new JsonArray();
        for (ResourceCost cost : plan.requirements()) {
            JsonObject value = new JsonObject();
            value.addProperty("resource", cost.resourceId()); value.addProperty("amount", cost.amount());
            value.addProperty("specification", cost.specification()); value.addProperty("matchMode", cost.matchMode().name());
            costs.add(value);
        }
        object.add("requirements", costs);
        JsonArray snapshots = new JsonArray();
        for (CostPaymentPlan.ResourceSnapshot snapshot : plan.snapshots()) {
            JsonObject value = new JsonObject();
            value.addProperty("resource", snapshot.resourceId()); value.addProperty("before", snapshot.before());
            value.addProperty("after", snapshot.after()); value.addProperty("reversible", snapshot.reversible());
            snapshots.add(value);
        }
        object.add("snapshots", snapshots);
        return object;
    }

    private static CostPaymentPlan receipt(JsonObject object) {
        List<ResourceCost> costs = new ArrayList<>();
        JsonArray requirements = required(object, "requirements").getAsJsonArray();
        JsonArray snapshots = required(object, "snapshots").getAsJsonArray();
        if (requirements.size() > 128 || snapshots.size() > 32) throw new IllegalArgumentException("Receipt too large");
        for (JsonElement element : requirements) {
            JsonObject value = element.getAsJsonObject();
            costs.add(new ResourceCost(string(value, "resource"), integer(value, "amount"),
                    string(value, "specification"), ResourceCost.MatchMode.valueOf(string(value, "matchMode"))));
        }
        List<CostPaymentPlan.ResourceSnapshot> actual = new ArrayList<>();
        for (JsonElement element : snapshots) {
            JsonObject value = element.getAsJsonObject();
            actual.add(new CostPaymentPlan.ResourceSnapshot(string(value, "resource"), string(value, "before"),
                    string(value, "after"), bool(value, "reversible")));
        }
        CostPaymentPlan plan = CostPaymentPlan.ready(integer(object, "units"), costs, actual).paid();
        boolean confirmed = bool(object, "confirmed"), uncertain = bool(object, "uncertain");
        if (confirmed && uncertain) throw new IllegalArgumentException("Invalid receipt confirmation");
        if (confirmed) plan = plan.withSnapshots(actual);
        return uncertain ? plan.uncertainReceipt() : plan;
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing operation field " + key);
        return value;
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Operation string required");
        return value.getAsString();
    }
    private static long number(JsonObject object, String key) {
        JsonElement value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Operation integer required");
        return value.getAsBigDecimal().longValueExact();
    }
    private static int integer(JsonObject object, String key) { return Math.toIntExact(number(object, key)); }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Operation boolean required");
        return value.getAsBoolean();
    }
    private static UUID uuid(JsonObject object, String key) {
        return UUID.fromString(LotteryProtocolLimits.token(string(object, key)));
    }
}
