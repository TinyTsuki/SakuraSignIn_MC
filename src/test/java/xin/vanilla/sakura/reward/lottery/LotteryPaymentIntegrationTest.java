package xin.vanilla.sakura.reward.lottery;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.api.cost.*;
import xin.vanilla.banira.common.cost.ResourcePaymentAccess;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.internal.neoforge.storage.NeoForgeLotteryOperationRepository;
import xin.vanilla.sakura.platform.LotteryOperationRepository;
import xin.vanilla.sakura.reward.Reward;

import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.*;

public class LotteryPaymentIntegrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Actor actor = new Actor();
    private final Runtime runtime = new Runtime();
    private final UUID session = UUID.randomUUID();
    private long nanos;
    private int seeds;
    private FailingRepository repository;

    private LotteryOperationService service() {
        if (repository == null) repository = new FailingRepository(new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath()));
        return new LotteryOperationService(runtime, repository, () -> 1_791_600_000_000L,
                () -> nanos, () -> 3L, () -> { seeds++; return 1L; }, () -> UUID.randomUUID().toString(), ZoneId.of("UTC"));
    }
    private LotteryQuote quote(LotteryOperationService service, int count) {
        return service.quote(actor, new LotteryQuoteRequest(session, 1, 3, "daily", count));
    }
    private LotteryDrawRequest draw(LotteryQuote quote) {
        return new LotteryDrawRequest(session, 2, 3, "daily", quote.getRequest().getCount(), quote.getQuoteToken());
    }

    @Test public void duplicateDrawBillsGrantsAndAdvancesQuotaOnce() throws Exception {
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, 2);
        assertTrue(quote.isReady()); assertEquals(0, seeds); assertEquals(10, actor.points);
        LotteryDrawRequest draw = draw(quote);
        assertTrue(service.draw(actor, draw).isSuccess());
        assertTrue(service.draw(actor, draw).isSuccess());
        assertEquals(6, actor.points); assertEquals(Arrays.asList(2, 1), runtime.grants);
        assertEquals(2, runtime.state.getTotalDraws()); assertEquals(1, seeds);
        LotteryOperation saved = repository.load(actor.id, draw.getOperationId());
        assertEquals(LotteryOperation.Stage.COMPLETED, saved.getStage());
        service.acknowledge(actor, saved.getOperationId(), saved.getDisplayToken());
        service.acknowledge(actor, saved.getOperationId(), saved.getDisplayToken());
        assertTrue(repository.load(actor.id, saved.getOperationId()).isAcknowledged());
        assertEquals(6, actor.points); assertEquals(2, runtime.grants.size());
    }

    @Test public void resourcesChangedAfterQuoteRejectEntireFixedBatch() throws Exception {
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, 2);
        actor.points = 3;
        assertFalse(service.draw(actor, draw(quote)).isSuccess());
        assertEquals(3, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(0, runtime.state.getTotalDraws()); assertEquals(0, seeds);
    }

    @Test public void allUsesMinimumOfResourceCapacityAndQuota() throws Exception {
        actor.points = 7; runtime.pool.setMaxDraws(2);
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, -1);
        assertEquals(2, quote.getAvailableCount());
        assertTrue(service.draw(actor, draw(quote)).isSuccess());
        assertEquals(3, actor.points); assertEquals(2, runtime.grants.size());
        assertEquals(2, runtime.state.getTotalDraws());
    }

    @Test public void insufficientQuotaNeverSilentlyTruncatesFixedRequest() throws Exception {
        runtime.pool.setMaxDraws(1);
        LotteryQuote quote = quote(service(), 2);
        assertFalse(quote.isReady()); assertEquals(10, actor.points); assertEquals(0, seeds);
    }

    @Test public void cooldownAllowsOnlyOneDrawInAllMode() throws Exception {
        runtime.pool.setLimitPolicy(LotteryLimitPolicy.COOLDOWN); runtime.pool.setCooldownSeconds(10);
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, -1);
        assertEquals(1, quote.getAvailableCount());
        assertTrue(service.draw(actor, draw(quote)).isSuccess());
        assertEquals(8, actor.points); assertEquals(1, runtime.grants.size());
    }

    @Test public void foreignPlayerTokenSessionAndExpiredQuoteCannotPay() throws Exception {
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, 2);
        Actor other = new Actor();
        assertFalse(service.draw(other, draw(quote)).isSuccess());
        assertFalse(service.draw(actor, new LotteryDrawRequest(UUID.randomUUID(), 2, 3,
                "daily", 2, quote.getQuoteToken())).isSuccess());
        nanos = 11_000_000_000L;
        assertFalse(service.draw(actor, draw(quote)).isSuccess());
        assertEquals(10, actor.points); assertEquals(10, other.points); assertTrue(runtime.grants.isEmpty());
    }

    @Test public void sameConnectionCannotResetThrottleWithAnotherSession() {
        LotteryOperationService service = service();
        assertTrue(quote(service, 1).isReady());
        assertFalse(service.quote(actor, new LotteryQuoteRequest(UUID.randomUUID(), 1, 3, "daily", 1)).isReady());
        for (int i = 2; i <= 8; i++) assertTrue(service.quote(actor,
                new LotteryQuoteRequest(session, i, 3, "daily", 1)).isReady());
        assertFalse(service.quote(actor, new LotteryQuoteRequest(session, 9, 3, "daily", 1)).isReady());
        assertEquals(0, seeds);
    }

    @Test public void permissionAndPoolContentRevalidatedAtCommit() throws Exception {
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, 2);
        actor.permitted = false;
        assertFalse(service.draw(actor, draw(quote)).isSuccess());
        actor.permitted = true; runtime.pool.setVoucherExpression("xpPoints(3)");
        assertFalse(service.draw(actor, draw(quote)).isSuccess());
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
    }

    @Test public void uncertainDebitNeverRetriesOnRecovery() throws Exception {
        actor.debitThrows = true;
        LotteryOperationService service = service(); LotteryDrawRequest draw = draw(quote(service, 2));
        assertFalse(service.draw(actor, draw).isSuccess());
        assertEquals(6, actor.points); actor.debitThrows = false;
        service().recover(actor);
        assertEquals(6, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(LotteryOperation.Stage.UNCERTAIN, repository.load(actor.id, draw.getOperationId()).getStage());
    }

    @Test public void paidWriteFailureFreezesPersistedPayingWithoutRebilling() throws Exception {
        LotteryOperationService service = service(); repository.failPaid = true;
        LotteryDrawRequest draw = draw(quote(service, 2));
        assertFalse(service.draw(actor, draw).isSuccess()); assertEquals(6, actor.points);
        repository.failPaid = false; service().recover(actor);
        assertEquals(6, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(LotteryOperation.Stage.UNCERTAIN, repository.load(actor.id, draw.getOperationId()).getStage());
    }

    @Test public void paidWithoutResultsRecoversOriginalPoolAndSeed() throws Exception {
        LotteryOperationService service = service(); repository.failResults = true;
        LotteryDrawRequest draw = draw(quote(service, 2));
        assertFalse(service.draw(actor, draw).isSuccess()); assertEquals(6, actor.points);
        repository.failResults = false;
        runtime.pool.getRewards().forEach(reward -> reward.getContent().addProperty("value", 99));
        service().recover(actor);
        assertEquals(Arrays.asList(2, 1), runtime.grants); assertEquals(6, actor.points);
        assertEquals(2, runtime.state.getTotalDraws()); assertEquals(1, seeds);
    }

    @Test public void grantPrefixWriteFailureFreezesInsteadOfRegranting() throws Exception {
        LotteryOperationService service = service(); repository.failPrefix = true;
        LotteryDrawRequest draw = draw(quote(service, 2));
        assertFalse(service.draw(actor, draw).isSuccess());
        assertEquals(Collections.singletonList(2), runtime.grants);
        repository.failPrefix = false; service().recover(actor);
        assertEquals(Collections.singletonList(2), runtime.grants); assertEquals(6, actor.points);
        assertEquals(LotteryOperation.Stage.UNCERTAIN, repository.load(actor.id, draw.getOperationId()).getStage());
    }

    @Test public void quotaSaveRetryUsesAbsoluteSnapshotAndDoesNotRegrantPrefix() throws Exception {
        LotteryOperationService service = service(); runtime.failQuota = true;
        LotteryDrawRequest draw = draw(quote(service, 2));
        LotteryDrawBatchResult pending = service.draw(actor, draw);
        assertFalse(pending.isSuccess()); assertEquals(1, runtime.state.getTotalDraws());
        assertEquals(1, pending.getRewards().size()); assertEquals("daily", pending.getPool().getId());
        runtime.failQuota = false; service().recover(actor);
        assertEquals(2, runtime.state.getTotalDraws()); assertEquals(Arrays.asList(2, 1), runtime.grants);
        assertEquals(6, actor.points);
        assertEquals(LotteryOperation.Stage.COMPLETED, repository.load(actor.id, draw.getOperationId()).getStage());
    }

    @Test public void notificationFailureAndAckCannotReopenBilling() throws Exception {
        LotteryOperationService service = service(); runtime.failNotify = true;
        LotteryDrawRequest draw = draw(quote(service, 2));
        assertTrue(service.draw(actor, draw).isSuccess());
        service.draw(actor, draw); service().recover(actor);
        assertEquals(Arrays.asList(2, 1), runtime.grants); assertEquals(6, actor.points);
        assertEquals(2, runtime.state.getTotalDraws());
    }

    @Test public void preflightRejectionRestoresBeforeAnyGrantButUnknownGrantDoesNot() throws Exception {
        LotteryOperationService service = service(); runtime.preflight = false;
        LotteryDrawRequest draw = draw(quote(service, 2)); service.draw(actor, draw);
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(LotteryOperation.Stage.ROLLED_BACK, repository.load(actor.id, draw.getOperationId()).getStage());
    }

    @Test public void throwingGrantPreservesIrreversibleSideEffectAndBlocksNewId() throws Exception {
        LotteryOperationService service = service(); runtime.grantThrows = true;
        LotteryDrawRequest draw = draw(quote(service, 2)); service.draw(actor, draw);
        assertEquals(Collections.singletonList(2), runtime.grants); assertEquals(6, actor.points);
        runtime.grantThrows = false; service().recover(actor);
        assertEquals(Collections.singletonList(2), runtime.grants);
        actor.connection = new Object();
        assertFalse(service().quote(actor, new LotteryQuoteRequest(UUID.randomUUID(), 1, 3, "daily", 1)).isReady());
    }

    @Test public void disconnectedPaidOperationResumesOnNewConnectionWithoutNewSeed() throws Exception {
        LotteryOperationService service = service(); repository.failResults = true;
        LotteryDrawRequest draw = draw(quote(service, 2)); service.draw(actor, draw);
        service.disconnect(actor.id); actor.live = false; repository.failResults = false;
        service.recover(actor); assertTrue(runtime.grants.isEmpty());
        actor.live = true; actor.connection = new Object(); service.recover(actor);
        assertEquals(Arrays.asList(2, 1), runtime.grants); assertEquals(6, actor.points); assertEquals(1, seeds);
    }
    @Test public void refundedDrawReplayDoesNotFreezeFutureRequests() throws Exception {
        LotteryOperationService service = service(); runtime.preflight = false;
        LotteryDrawRequest request = draw(quote(service, 2));
        assertEquals(LotteryDrawStatus.GRANT_FAILED, service.draw(actor, request).getStatus());
        assertEquals(LotteryDrawStatus.GRANT_FAILED, service.draw(actor, request).getStatus());
        runtime.preflight = true;
        assertTrue(service.quote(actor, new LotteryQuoteRequest(session, 3, 3, "daily", 1)).isReady());
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
    }
    @Test public void replacementPlayerOnSameConnectionKeepsAuthenticatedSession() throws Exception {
        LotteryOperationService service = service(); LotteryQuote quote = quote(service, 2);
        Actor replacement = new Actor(actor.id); replacement.connection = actor.connection; actor.live = false;
        assertTrue(service.draw(replacement, draw(quote)).isSuccess());
        assertEquals(6, replacement.points); assertEquals(2, runtime.grants.size());
    }
    @Test public void preparedWriteFailureNeverDebits() throws Exception {
        LotteryOperationService service = service(); repository.failPrepared = true;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
        assertNull(repository.load(actor.id, request.getOperationId()));
    }
    @Test public void payingWriteFailureCancelsWithoutDebitingOnRecovery() throws Exception {
        LotteryOperationService service = service(); repository.failPaying = true;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        repository.failPaying = false; service().recover(actor);
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(LotteryOperation.Stage.ROLLED_BACK, repository.load(actor.id, request.getOperationId()).getStage());
    }
    @Test public void grantIntentWriteFailureResumesBeforeAnyExternalGrant() throws Exception {
        LotteryOperationService service = service(); repository.failGrantIntent = true;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        assertEquals(6, actor.points); assertTrue(runtime.grants.isEmpty());
        repository.failGrantIntent = false; service().recover(actor);
        assertEquals(Arrays.asList(2, 1), runtime.grants); assertEquals(6, actor.points);
    }
    @Test public void completionWriteFailureDoesNotRegrantOrAdvanceQuotaTwice() throws Exception {
        LotteryOperationService service = service(); repository.failCompletion = true;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        repository.failCompletion = false; service().recover(actor);
        assertEquals(Arrays.asList(2, 1), runtime.grants); assertEquals(6, actor.points);
        assertEquals(2, runtime.state.getTotalDraws());
        assertEquals(LotteryOperation.Stage.COMPLETED, repository.load(actor.id, request.getOperationId()).getStage());
    }
    @Test public void refundTerminalWriteFailureCannotGrantAfterResourcesWereRestored() throws Exception {
        LotteryOperationService service = service(); repository.failRollback = true; runtime.preflight = false;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
        repository.failRollback = false; runtime.preflight = true; service().recover(actor);
        assertEquals(10, actor.points); assertTrue(runtime.grants.isEmpty());
        assertEquals(LotteryOperation.Stage.UNCERTAIN, repository.load(actor.id, request.getOperationId()).getStage());
    }
    @Test public void receiptReadFailureRetainsPaidUncertainWithoutRebilling() throws Exception {
        LotteryOperationService service = service(); actor.receiptThrows = true;
        LotteryDrawRequest request = draw(quote(service, 2)); service.draw(actor, request);
        actor.receiptThrows = false; service().recover(actor);
        assertEquals(6, actor.points); assertTrue(runtime.grants.isEmpty());
        LotteryOperation stored = repository.load(actor.id, request.getOperationId());
        assertEquals(LotteryOperation.Stage.UNCERTAIN, stored.getStage());
        assertTrue(stored.getReceipt().isCommitted()); assertFalse(stored.getReceipt().hasConfirmedSnapshots());
    }

    static final class Actor {
        final UUID id; Object connection = new Object();
        Actor() { this(UUID.randomUUID()); }
        Actor(UUID id) { this.id = id; }
        int points = 10; boolean live = true, permitted = true, debitThrows, receiptThrows;
    }
    final class Runtime implements LotteryOperationService.Runtime {
        LotteryPool pool = LotteryOperationTest.pool(); LotteryDrawState state = new LotteryDrawState("daily");
        final List<Integer> grants = new ArrayList<>();
        boolean preflight = true, grantThrows, failQuota, failNotify;
        public UUID playerId(Object player) { return ((Actor) player).id; }
        public Object connection(Object player) { return ((Actor) player).live ? ((Actor) player).connection : null; }
        public boolean permitted(Object player) { return ((Actor) player).permitted; }
        public LotteryPool pool(String id) { return "daily".equals(id) ? pool : null; }
        public LotteryDrawState quota(Object player, String id) { return state; }
        public CostAccess costs(Object player) {
            Actor actor = (Actor) player;
            CostResourceAccess points = new CostResourceAccess() {
                public String snapshot() {
                    if (actor.receiptThrows && actor.points == 6) throw new IllegalStateException("injected receipt");
                    return Integer.toString(actor.points);
                }
                public CostFailure check(ResourceCost cost, int units, String before) {
                    return Integer.parseInt(before) >= cost.amount() * units ? CostFailure.NONE : CostFailure.INSUFFICIENT_RESOURCE;
                }
                public String after(ResourceCost cost, int units, String before) { return Integer.toString(Integer.parseInt(before) - cost.amount() * units); }
                public CostFailure debit(String before, String after) {
                    if (!snapshot().equals(before)) return CostFailure.UNCERTAIN;
                    actor.points = Integer.parseInt(after);
                    if (actor.debitThrows) throw new IllegalStateException("injected after debit");
                    return CostFailure.NONE;
                }
                public CostFailure restore(String before, String after) {
                    if (!snapshot().equals(after)) return CostFailure.UNCERTAIN;
                    actor.points = Integer.parseInt(before); return CostFailure.NONE;
                }
                public boolean reversible() { return true; }
            };
            return new ResourcePaymentAccess(Collections.singletonMap("banira:xp_point", points),
                    () -> actor.live ? CostFailure.NONE : CostFailure.ACTOR_UNAVAILABLE);
        }
        public boolean canGrant(Object player, Reward reward) { return preflight; }
        public boolean grant(Object player, Reward reward, LotteryOperation operation) {
            grants.add(reward.getContent().get("value").getAsInt());
            if (grantThrows) throw new IllegalStateException("injected after grant"); return true;
        }
        public void saveQuota(Object player, LotteryDrawState state) {
            this.state = state;
            if (failQuota) throw new IllegalStateException("injected quota save");
        }
        public void completed(Object player, LotteryOperation operation, LotteryDrawBatchResult result, boolean recovered) {
            if (failNotify) throw new IllegalStateException("injected notification");
        }
    }
    static final class FailingRepository implements LotteryOperationRepository {
        final LotteryOperationRepository delegate;
        boolean failPaid, failResults, failPrefix, failPrepared, failPaying, failGrantIntent, failCompletion, failRollback;
        FailingRepository(LotteryOperationRepository delegate) { this.delegate = delegate; }
        public LotteryOperation load(UUID id, String op) throws IOException { return delegate.load(id, op); }
        public List<LotteryOperation> list(UUID id) throws IOException { return delegate.list(id); }
        public List<LotteryOperation> incomplete(UUID id) throws IOException { return delegate.incomplete(id); }
        public void flush() throws IOException { delegate.flush(); }
        public void write(LotteryOperation op) throws IOException {
            if ((failPaid && op.getStage() == LotteryOperation.Stage.PAID)
                    || (failResults && !op.getRewards().isEmpty()) || (failPrefix && op.getGrantedCount() == 1)
                    || (failPrepared && op.getStage() == LotteryOperation.Stage.PREPARED)
                    || (failPaying && op.getStage() == LotteryOperation.Stage.PAYING)
                    || (failGrantIntent && op.getStage() == LotteryOperation.Stage.GRANTING)
                    || (failCompletion && op.getStage() == LotteryOperation.Stage.COMPLETED)
                    || (failRollback && op.getStage() == LotteryOperation.Stage.ROLLED_BACK))
                throw new IOException("injected journal boundary");
            delegate.write(op);
        }
    }
}
