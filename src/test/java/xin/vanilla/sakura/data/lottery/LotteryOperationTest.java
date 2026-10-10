package xin.vanilla.sakura.data.lottery;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.banira.api.cost.CostPaymentPlan;
import xin.vanilla.banira.api.cost.ResourceCost;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryOperationTest {
    public static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID SESSION = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    public static LotteryPool pool() {
        JsonObject one = new JsonObject(); one.addProperty("value", 1);
        JsonObject two = new JsonObject(); two.addProperty("value", 2);
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 10, 0,
                new RewardList(Arrays.asList(new Reward(one, SakuraRewardTypes.EXPERIENCE_POINT),
                        new Reward(two, SakuraRewardTypes.EXPERIENCE_POINT))));
        pool.setVoucherExpression("xpPoints(2)");
        return pool;
    }

    public static LotteryOperation prepared(long sequence) {
        return LotteryOperation.prepare(PLAYER, new LotteryDrawRequest(SESSION, sequence, 3,
                "daily", 2, "cccccccc-cccc-cccc-cccc-cccccccccccc"), pool(), 2,
                1L, 1_791_600_000_000L, "dddddddd-dddd-dddd-dddd-dddddddddddd");
    }

    public static CostPaymentPlan receipt() {
        return CostPaymentPlan.ready(2,
                Collections.singletonList(new ResourceCost("banira:xp_points", 4, "")),
                Collections.singletonList(new CostPaymentPlan.ResourceSnapshot("banira:xp_points",
                        "{\"points\":10}", "{\"points\":6}", true))).paid().withSnapshots(
                Collections.singletonList(new CostPaymentPlan.ResourceSnapshot("banira:xp_points",
                        "{\"points\":10}", "{\"points\":6}", true)));
    }

    @Test public void snapshotsDoNotAliasMutableConfigurationOrReturnedRewards() {
        LotteryPool source = pool();
        LotteryOperation op = LotteryOperation.prepare(PLAYER, prepared(1).getRequest(), source,
                2, 1, 100, "dddddddd-dddd-dddd-dddd-dddddddddddd");
        source.getRewards().get(0).getContent().addProperty("value", 900);
        source.setVoucherExpression("xpPoints(900)");
        op.getPool().getRewards().clear();
        assertEquals("xpPoints(2)", op.getPool().getVoucherExpression());
        assertEquals(1, op.getPool().getRewards().get(0).getContent().get("value").getAsInt());
        assertEquals(2, op.getPool().getRewards().size());
    }

    @Test public void selectionRequiresConfirmedPaymentAndUsesStoredSeed() throws Exception {
        LotteryOperation before = prepared(1);
        reject(before::selectRewards);
        LotteryOperation selected = before.paying().paid(receipt()).selectRewards();
        assertEquals(2, selected.getRewards().get(0).getContent().get("value").getAsInt());
        assertEquals(1, selected.getRewards().get(1).getContent().get("value").getAsInt());
        selected.getRewards().get(0).getContent().addProperty("value", 100);
        assertEquals(2, selected.getRewards().get(0).getContent().get("value").getAsInt());
        assertEquals(new LotteryOperationCodec().encode(selected),
                new LotteryOperationCodec().encode(selected.selectRewards()));
    }

    @Test public void restoredPaidOperationKeepsOriginalPoolDespiteConfigReload() throws Exception {
        LotteryOperationCodec codec = new LotteryOperationCodec();
        String saved = codec.encode(prepared(1).paying().paid(receipt()));
        LotteryPool changed = pool(); changed.getRewards().clear();
        LotteryOperation restored = codec.decode(saved).selectRewards();
        assertEquals(2, restored.getRewards().size());
        assertEquals(2, restored.getRewards().get(0).getContent().get("value").getAsInt());
        assertEquals(1, restored.getRewards().get(1).getContent().get("value").getAsInt());
    }

    @Test public void grantProgressOnlyAdvancesAfterGrantingAndCannotReopenPayment() {
        LotteryOperation paid = prepared(1).paying().paid(receipt()).selectRewards();
        reject(paid::grantSucceeded);
        LotteryOperation first = paid.granting().grantSucceeded();
        assertEquals(1, first.getGrantedCount());
        assertEquals(LotteryOperation.Stage.PAID, first.getStage());
        LotteryOperation done = first.granting().grantSucceeded().complete();
        assertEquals(LotteryOperation.Stage.COMPLETED, done.getStage());
        assertEquals(2, done.getGrantedCount());
        reject(done::paying); reject(done::granting); reject(done::rolledBack); reject(first::rolledBack);
    }

    @Test public void ambiguousPaymentOrGrantFreezesWithoutLosingConfirmedPrefix() {
        LotteryOperation paying = prepared(1).paying().recover();
        assertEquals(LotteryOperation.Stage.UNCERTAIN, paying.getStage());
        reject(paying::paying);
        LotteryOperation partial = prepared(2).paying().paid(receipt()).selectRewards()
                .granting().grantSucceeded().granting().recover();
        assertEquals(LotteryOperation.Stage.UNCERTAIN, partial.getStage());
        assertEquals(1, partial.getGrantedCount()); assertEquals(2, partial.getRewards().size());
        reject(partial::granting); reject(partial::rolledBack);
    }

    @Test public void unpaidPreparedRecoveryCancelsWithoutCreatingReceipt() {
        LotteryOperation restored = prepared(1).recover();
        assertEquals(LotteryOperation.Stage.ROLLED_BACK, restored.getStage());
        assertNull(restored.getReceipt()); assertTrue(restored.getRewards().isEmpty());
    }

    @Test public void uncertainReceiptCannotAuthorizeGrant() {
        LotteryOperation op = prepared(1).paying().paid(receipt().uncertainReceipt());
        assertEquals(LotteryOperation.Stage.UNCERTAIN, op.getStage());
        reject(op::selectRewards);
        reject(() -> prepared(1).paying().paid(CostPaymentPlan.ready(2,
                Collections.emptyList(), Collections.emptyList()).paid()));
    }

    @Test public void codecKeepsConfirmedReceiptAndGrantPrefix() throws Exception {
        LotteryOperationCodec codec = new LotteryOperationCodec();
        LotteryOperation op = prepared(1).paying().paid(receipt()).selectRewards().granting().grantSucceeded();
        LotteryOperation copy = codec.decode(codec.encode(op));
        assertEquals(1, copy.getGrantedCount()); assertTrue(copy.getReceipt().hasConfirmedSnapshots());
        assertEquals("{\"points\":6}", copy.getReceipt().snapshots().get(0).after());
        assertEquals(receipt().requirements(), copy.getReceipt().requirements());
        assertEquals(codec.encode(op), codec.encode(copy));
    }

    @Test public void rejectsMalformedOrInconsistentPersistence() throws Exception {
        LotteryOperationCodec codec = new LotteryOperationCodec();
        String original = codec.encode(prepared(1));
        for (String field : new String[]{"schemaVersion", "playerId", "seed", "stage", "pool"}) {
            JsonObject object = new com.google.gson.JsonParser().parse(original).getAsJsonObject();
            object.remove(field); rejectIo(() -> codec.decode(object.toString()));
        }
        JsonObject object = new com.google.gson.JsonParser().parse(original).getAsJsonObject();
        object.addProperty("grantedCount", 2); rejectIo(() -> codec.decode(object.toString()));
        object.addProperty("grantedCount", 0); object.addProperty("seed", new BigDecimal("1.5"));
        rejectIo(() -> codec.decode(object.toString()));
        object.addProperty("seed", 1); object.addProperty("stage", "PAID");
        rejectIo(() -> codec.decode(object.toString()));
    }

    @Test public void rejectsPoolMismatchInvalidCountAndEmptyEligiblePool() {
        LotteryPool wrong = pool(); wrong.setId("other");
        reject(() -> LotteryOperation.prepare(PLAYER, prepared(1).getRequest(), wrong,
                2, 1, 100, "dddddddd-dddd-dddd-dddd-dddddddddddd"));
        reject(() -> LotteryOperation.prepare(PLAYER, prepared(1).getRequest(), pool(),
                1, 1, 100, "dddddddd-dddd-dddd-dddd-dddddddddddd"));
        LotteryPool empty = pool(); empty.getRewards().forEach(reward -> reward.setDisabled(true));
        reject(() -> LotteryOperation.prepare(PLAYER, prepared(1).getRequest(), empty,
                2, 1, 100, "dddddddd-dddd-dddd-dddd-dddddddddddd"));
    }

    @Test public void emptyReceiptCannotAuthorizePaidPool() {
        CostPaymentPlan free = CostPaymentPlan.ready(2, Collections.emptyList(), Collections.emptyList())
                .paid().withSnapshots(Collections.emptyList());
        reject(() -> prepared(1).paying().paid(free));
    }

    @Test public void uncertainReceiptCannotBePersistedAsRolledBack() throws Exception {
        LotteryOperationCodec codec = new LotteryOperationCodec();
        JsonObject object = new com.google.gson.JsonParser().parse(codec.encode(
                prepared(1).paying().paid(receipt().uncertainReceipt()))).getAsJsonObject();
        object.addProperty("stage", "ROLLED_BACK");
        rejectIo(() -> codec.decode(object.toString()));
    }

    @Test public void largeWeightsRemainDeterministicWithoutDoubleOverflow() {
        LotteryPool pool = pool();
        pool.getRewards().forEach(reward -> reward.setProbability(new BigDecimal("1e400")));
        LotteryOperation selected = LotteryOperation.prepare(PLAYER, prepared(1).getRequest(), pool,
                2, 1, 100, "dddddddd-dddd-dddd-dddd-dddddddddddd").paying().paid(receipt()).selectRewards();
        assertEquals(2, selected.getRewards().get(0).getContent().get("value").getAsInt());
        assertEquals(1, selected.getRewards().get(1).getContent().get("value").getAsInt());
    }

    @Test public void missingDuplicateOrForeignReceiptSnapshotsCannotBeConfirmed() throws Exception {
        LotteryOperationCodec codec = new LotteryOperationCodec();
        String original = codec.encode(prepared(1).paying().paid(receipt()));
        for (int mode = 0; mode < 3; mode++) {
            JsonObject object = new com.google.gson.JsonParser().parse(original).getAsJsonObject();
            com.google.gson.JsonArray snapshots = object.getAsJsonObject("receipt").getAsJsonArray("snapshots");
            if (mode == 0) snapshots.remove(0);
            if (mode == 1) snapshots.add(new com.google.gson.JsonParser().parse(snapshots.get(0).toString()));
            if (mode == 2) snapshots.get(0).getAsJsonObject().addProperty("resource", "foreign:resource");
            rejectIo(() -> codec.decode(object.toString()));
        }
    }

    public static void reject(Runnable action) {
        try { action.run(); fail("Invalid operation accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    private interface IoAction { void run() throws Exception; }
    private static void rejectIo(IoAction action) throws Exception {
        try { action.run(); fail("Invalid persisted operation accepted"); }
        catch (java.io.IOException expected) { }
    }
}
