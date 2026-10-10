package xin.vanilla.sakura.reward.lottery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.api.cost.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.platform.LotteryOperationRepository;
import xin.vanilla.sakura.reward.Reward;

import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Level-owned, server-thread draw coordinator; billing belongs to Banira. */
public final class LotteryOperationService {
    public interface Runtime {
        UUID playerId(Object player);
        Object connection(Object player);
        boolean permitted(Object player);
        LotteryPool pool(String id);
        LotteryDrawState quota(Object player, String poolId);
        CostAccess costs(Object player);
        boolean canGrant(Object player, Reward reward);
        boolean grant(Object player, Reward reward, LotteryOperation operation);
        void saveQuota(Object player, LotteryDrawState state);
        void completed(Object player, LotteryOperation operation, LotteryDrawBatchResult result, boolean recovered);
    }
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long QUOTE_NANOS = 10_000_000_000L;
    private final Thread owner = Thread.currentThread();
    private final Runtime runtime;
    private final LotteryOperationRepository repository;
    private final LongSupplier clock, nanos, generation, seed;
    private final Supplier<String> tokens;
    private final ZoneId zone;
    private final CostQuoteSessions sessions;
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    private final Set<UUID> blocked = new HashSet<>();
    private final UUID commandSession = UUID.randomUUID();
    private long commandSequence;

    public LotteryOperationService(Runtime runtime, LotteryOperationRepository repository, LongSupplier clock,
                                   LongSupplier nanos, LongSupplier generation, LongSupplier seed,
                                   Supplier<String> tokens, ZoneId zone) {
        this.runtime = Objects.requireNonNull(runtime); this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock); this.nanos = Objects.requireNonNull(nanos);
        this.generation = Objects.requireNonNull(generation); this.seed = Objects.requireNonNull(seed);
        this.tokens = Objects.requireNonNull(tokens); this.zone = Objects.requireNonNull(zone);
        sessions = new CostQuoteSessions(nanos, generation, id -> {
            Binding binding = bindings.get(id);
            return binding != null && runtime.connection(binding.player) == binding.connection;
        });
    }

    public LotteryQuote quote(Object player, LotteryQuoteRequest request) {
        requireOwner(); long revision = generation.getAsLong();
        if (!live(player) || !runtime.permitted(player)) return rejected(request, LotteryQuote.Status.FORBIDDEN, revision);
        UUID id = runtime.playerId(player);
        if (!bind(player, request.getSession()) || sessions.accept(id, request.getSession(),
                request.getRequestId(), request.getGeneration()).isFailure())
            return rejected(request, request.getGeneration() != revision ? LotteryQuote.Status.STALE_GENERATION
                    : LotteryQuote.Status.PAYMENT_UNAVAILABLE, revision);
        tickets.remove(id);
        LotteryPool pool = runtime.pool(request.getPoolId());
        if (pool == null) return rejected(request, LotteryQuote.Status.POOL_NOT_FOUND, revision);
        try {
            if (blocked.contains(id) || !repository.incomplete(id).isEmpty() || !hasRewards(pool))
                return rejected(request, LotteryQuote.Status.PAYMENT_UNAVAILABLE, revision);
            LotteryPool snapshot = LotteryPools.copy(pool);
            int count = capacity(player, snapshot, runtime.costs(player));
            if (count < 1 || request.getCount() > count)
                return rejected(request, LotteryQuote.Status.PAYMENT_UNAVAILABLE, revision);
            String token = LotteryProtocolLimits.token(tokens.get());
            tickets.put(id, new Ticket(request, token, snapshot, nanos.getAsLong()));
            return new LotteryQuote(request, LotteryQuote.Status.READY, revision, count,
                    snapshot.getVoucherExpression(), token);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Lottery quote unavailable for " + id, failure);
            return rejected(request, LotteryQuote.Status.PAYMENT_UNAVAILABLE, revision);
        }
    }

    public LotteryDrawBatchResult draw(Object player, LotteryDrawRequest request) {
        requireOwner();
        if (!live(player) || !runtime.permitted(player)) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
        UUID id = runtime.playerId(player); Binding binding = bindings.get(id);
        if (binding == null || binding.connection != runtime.connection(player)
                || !binding.session.equals(request.getRequest().getSession())) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
        binding.player = player;
        CostFailure accepted = sessions.accept(id, binding.session, request.getRequest().getRequestId(), request.getRequest().getGeneration());
        if (accepted.equals(CostFailure.RATE_LIMITED)) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
        try {
            LotteryOperation existing = repository.load(id, request.getOperationId());
            if (existing != null) {
                if (!existing.getRequest().equals(request)) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
                return blocked.contains(id) ? result(existing) : finish(player, existing.recover(), true);
            }
            Ticket ticket = tickets.remove(id);
            if (accepted.isFailure() || ticket == null || !ticket.token.equals(request.getQuoteToken())
                    || !sameSelection(ticket.request, request.getRequest())
                    || nanos.getAsLong() - ticket.created >= QUOTE_NANOS
                    || !ticket.pool.equals(runtime.pool(ticket.pool.getId()))) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
            return start(player, request, ticket.pool);
        } catch (IOException | RuntimeException failure) { return unavailable(id, failure); }
    }

    public LotteryDrawBatchResult commandDraw(Object player, String poolId, int count) {
        requireOwner();
        if (count != -1 && (count < 1 || count > 100)) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
        if (!live(player) || !runtime.permitted(player)) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null);
        UUID id = runtime.playerId(player);
        try {
            LotteryPool pool = runtime.pool(poolId);
            if (pool == null) return failed(LotteryDrawStatus.POOL_NOT_FOUND, null);
            long sequence = commandSequence = Math.incrementExact(commandSequence);
            LotteryDrawRequest request = new LotteryDrawRequest(commandSession, sequence, generation.getAsLong(), poolId, count, tokens.get());
            return start(player, request, LotteryPools.copy(pool));
        } catch (IOException | RuntimeException failure) { return unavailable(id, failure); }
    }

    private LotteryDrawBatchResult start(Object player, LotteryDrawRequest request, LotteryPool pool) throws IOException {
        UUID id = runtime.playerId(player);
        if (blocked.contains(id) || !repository.incomplete(id).isEmpty()) return failed(LotteryDrawStatus.OPERATION_PENDING, pool);
        if (!hasRewards(pool)) return failed(LotteryDrawStatus.EMPTY_POOL, pool);
        CostAccess access = runtime.costs(player);
        int available = capacity(player, pool, access), requested = request.getRequest().getCount();
        if (available < 1 || requested > available) return failed(LotteryDrawStatus.PAYMENT_UNAVAILABLE, pool);
        int count = requested == -1 ? available : requested;
        LotteryOperation operation = LotteryOperation.prepare(id, request, pool, count, seed.getAsLong(), clock.getAsLong(),
                tokens.get(), runtime.quota(player, pool.getId()), zone);
        repository.write(operation);
        operation = operation.paying(); repository.write(operation);
        Payment context = new Payment(player, operation, access, BaniraCosts.parse(pool.getVoucherExpression()));
        CostPaymentService<Payment> payments = new CostPaymentService<>(
                value -> value.access.preview(value.requirement, value.operation.getCount()), this::identity,
                new CostPaymentService.PaymentAccess<Payment>() {
                    public CostFailure validate(Payment value) {
                        LotteryOperation op = value.operation;
                        return live(value.player) && runtime.permitted(value.player)
                                && generation.getAsLong() == op.getRequest().getRequest().getGeneration()
                                && op.getPool().equals(runtime.pool(op.getPool().getId()))
                                && op.getQuotaBefore().equals(runtime.quota(value.player, op.getPool().getId()))
                                ? CostFailure.NONE : CostFailure.CONFIGURATION_UNAVAILABLE;
                    }
                    public CostFailure pay(Payment value, CostPaymentPlan plan) {
                        try {
                            CostFailure checked = value.access.recheck(plan);
                            return checked.isFailure() ? checked : value.access.debit(plan);
                        } catch (RuntimeException unknown) { LOGGER.error("Lottery resource debit uncertain", unknown); return CostFailure.UNCERTAIN; }
                    }
                    public List<CostPaymentPlan.ResourceSnapshot> receipt(Payment value, CostPaymentPlan plan) {
                        CostPaymentPlan actual = value.access.receipt(plan);
                        if (!actual.isCommitted() || !actual.hasConfirmedSnapshots() || actual.recoveryFailure().isFailure())
                            throw new IllegalStateException("Unconfirmed lottery payment receipt");
                        return actual.snapshots();
                    }
                    public void afterPayment(Payment value, CostPaymentPlan plan) { value.access.afterPayment(plan); }
                });
        CostPaymentPlan paid = payments.commit(CostOperation.create(identity(context)), context);
        if (!paid.isCommitted()) {
            operation = paid.failure().equals(CostFailure.UNCERTAIN) ? operation.uncertain() : operation.rolledBack();
            repository.write(operation); return result(operation);
        }
        operation = operation.paid(paid); repository.write(operation);
        return finish(player, operation, false);
    }

    private CostIdentity identity(Payment context) {
        LotteryQuoteRequest request = context.operation.getRequest().getRequest();
        LotteryPool current = runtime.pool(request.getPoolId());
        return new CostIdentity("sakura:lottery", runtime.playerId(context.player), request.getRequestId(), generation.getAsLong(),
                context.operation.getOperationId(), current == null ? "missing" : current.getVoucherExpression());
    }

    private LotteryDrawBatchResult finish(Object player, LotteryOperation operation, boolean recovered) throws IOException {
        if (blocked.contains(operation.getPlayerId())) return result(operation);
        if (operation.getStage() == LotteryOperation.Stage.PREPARED || operation.getStage() == LotteryOperation.Stage.PAYING
                || operation.getStage() == LotteryOperation.Stage.GRANTING || operation.isRollbackPending()) {
            operation = operation.recover(); repository.write(operation);
        }
        if (operation.getStage() == LotteryOperation.Stage.UNCERTAIN) {
            if (operation.getGrantedCount() > 0) runtime.saveQuota(player, operation.getQuotaAfter());
            return result(operation);
        }
        if (operation.getStage() == LotteryOperation.Stage.PAID) {
            operation = operation.selectRewards(); repository.write(operation);
            if (operation.getGrantedCount() > 0) runtime.saveQuota(player, operation.getQuotaAfter());
            while (operation.getGrantedCount() < operation.getCount()) {
                if (!live(player) || !runtime.permitted(player)) return result(operation);
                Reward reward = operation.getRewards().get(operation.getGrantedCount());
                if (!runtime.canGrant(player, reward)) {
                    if (operation.getGrantedCount() == 0) {
                        operation = operation.beginRollback(); repository.write(operation);
                        CostFailure restored;
                        try { restored = runtime.costs(player).restore(operation.getReceipt()); }
                        catch (RuntimeException unknown) { restored = CostFailure.UNCERTAIN; }
                        operation = restored.isFailure() ? operation.uncertain() : operation.rolledBack();
                        repository.write(operation);
                    } else { operation = operation.uncertain(); repository.write(operation); }
                    return result(operation);
                }
                operation = operation.granting(); repository.write(operation);
                boolean granted;
                try { granted = runtime.grant(player, reward, operation); }
                catch (RuntimeException unknown) { LOGGER.error("Lottery grant uncertain", unknown); granted = false; }
                if (!granted) { operation = operation.uncertain(); repository.write(operation); return result(operation); }
                operation = operation.grantSucceeded(); repository.write(operation);
                runtime.saveQuota(player, operation.getQuotaAfter());
            }
            operation = operation.complete(); repository.write(operation);
        }
        LotteryDrawBatchResult result = result(operation);
        if (operation.getStage() == LotteryOperation.Stage.COMPLETED && !operation.isAcknowledged()) {
            try { runtime.completed(player, operation, result, recovered); }
            catch (RuntimeException followUp) { LOGGER.error("Lottery completion display failed for " + operation.getOperationId(), followUp); }
        }
        return result;
    }

    public void acknowledge(Object player, String operationId, String token) {
        requireOwner(); if (!live(player)) return; UUID id = runtime.playerId(player);
        try {
            LotteryOperation operation = repository.load(id, operationId);
            if (operation != null && operation.getStage() == LotteryOperation.Stage.COMPLETED
                    && operation.getDisplayToken().equals(token) && !operation.isAcknowledged()) repository.write(operation.acknowledge());
        } catch (IOException | RuntimeException failure) { unavailable(id, failure); }
    }
    public void recover(Object player) {
        requireOwner(); if (!live(player) || !runtime.permitted(player)) return; UUID id = runtime.playerId(player);
        if (blocked.contains(id)) return;
        try {
            for (LotteryOperation operation : repository.incomplete(id)) finish(player, operation, true);
            List<LotteryOperation> completed = repository.list(id);
            completed.removeIf(op -> op.getStage() != LotteryOperation.Stage.COMPLETED || op.isAcknowledged());
            if (!completed.isEmpty()) {
                completed.sort(Comparator.comparingLong(LotteryOperation::getCreatedAt));
                LotteryOperation latest = completed.get(completed.size() - 1);
                try { runtime.completed(player, latest, result(latest), true); }
                catch (RuntimeException followUp) { LOGGER.error("Lottery recovery display failed", followUp); }
            }
        } catch (IOException | RuntimeException failure) { unavailable(id, failure); }
    }
    public void disconnect(UUID player) {
        requireOwner(); sessions.close(player); bindings.remove(player); tickets.remove(player); blocked.remove(player);
    }
    public void close() throws IOException {
        requireOwner(); sessions.clear(); bindings.clear(); tickets.clear(); repository.flush();
    }

    private boolean bind(Object player, UUID session) {
        UUID id = runtime.playerId(player); Object connection = runtime.connection(player);
        Binding binding = bindings.get(id);
        if (binding != null && binding.connection != connection) { disconnect(id); binding = null; }
        if (binding == null) {
            if (bindings.size() >= 1024) return false;
            bindings.put(id, new Binding(player, connection, session)); sessions.open(id, session); return true;
        }
        if (!binding.session.equals(session)) return false;
        binding.player = player; return true;
    }
    private boolean live(Object player) { return player != null && runtime.connection(player) != null; }
    private boolean hasRewards(LotteryPool pool) {
        return pool.getRewards().stream().anyMatch(reward -> reward != null && !reward.isDisabled()
                && reward.getProbability().signum() > 0);
    }
    private int capacity(Object player, LotteryPool pool, CostAccess access) {
        LotteryLimitResult limit = LotteryDrawLimiter.evaluate(pool, runtime.quota(player, pool.getId()), clock.getAsLong(), zone);
        if (!limit.isAllowed()) return 0;
        int upper = Math.min(100, limit.getRemainingDraws());
        if (pool.getLimitPolicy() == LotteryLimitPolicy.COOLDOWN || pool.getCooldownSeconds() > 0) upper = Math.min(1, upper);
        CostRequirement requirement = BaniraCosts.parse(pool.getVoucherExpression());
        int lo = 0, hi = upper;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (access.preview(requirement, mid).isSuccess()) lo = mid; else hi = mid - 1;
        }
        return lo;
    }
    private LotteryQuote rejected(LotteryQuoteRequest request, LotteryQuote.Status status, long revision) {
        return new LotteryQuote(request, status, revision, 0, "", "");
    }
    private LotteryDrawBatchResult failed(LotteryDrawStatus status, LotteryPool pool) {
        return new LotteryDrawBatchResult(status, pool, Collections.emptyList(), 0, 0);
    }
    private LotteryDrawBatchResult unavailable(UUID id, Exception failure) {
        blocked.add(id); tickets.remove(id); LOGGER.error("Lottery operation frozen for " + id, failure);
        try {
            List<LotteryOperation> pending = repository.incomplete(id);
            if (pending.size() == 1) return result(pending.get(0));
        } catch (IOException | RuntimeException unreadable) { failure.addSuppressed(unreadable); }
        return failed(LotteryDrawStatus.OPERATION_PENDING, null);
    }
    private LotteryDrawBatchResult result(LotteryOperation operation) {
        LotteryDrawStatus status = operation.getStage() == LotteryOperation.Stage.COMPLETED ? LotteryDrawStatus.SUCCESS
                : operation.getStage() == LotteryOperation.Stage.UNCERTAIN ? LotteryDrawStatus.OPERATION_UNCERTAIN
                : operation.getStage() == LotteryOperation.Stage.ROLLED_BACK ? LotteryDrawStatus.GRANT_FAILED : LotteryDrawStatus.OPERATION_PENDING;
        List<Reward> rewards = operation.getRewards();
        rewards = new ArrayList<>(rewards.subList(0, operation.getGrantedCount()));
        LotteryLimitResult after = LotteryDrawLimiter.evaluate(operation.getPool(), operation.getQuotaAfter(),
                operation.getCreatedAt(), ZoneId.of(operation.getZoneId()));
        return new LotteryDrawBatchResult(status, operation.getPool(), rewards, after.getRetryAfterSeconds(), after.getRemainingDraws());
    }
    private static boolean sameSelection(LotteryQuoteRequest quote, LotteryQuoteRequest draw) {
        return quote.getSession().equals(draw.getSession()) && quote.getGeneration() == draw.getGeneration()
                && quote.getPoolId().equals(draw.getPoolId()) && quote.getCount() == draw.getCount()
                && draw.getRequestId() > quote.getRequestId();
    }
    private static final class Payment {
        final Object player; final LotteryOperation operation; final CostAccess access; final CostRequirement requirement;
        Payment(Object player, LotteryOperation operation, CostAccess access, CostRequirement requirement) {
            this.player = player; this.operation = operation; this.access = access; this.requirement = requirement;
        }
    }
    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Lottery requires its owning server thread");
    }
    private static final class Binding {
        Object player; final Object connection; final UUID session;
        Binding(Object player, Object connection, UUID session) { this.player = player; this.connection = connection; this.session = session; }
    }
    private static final class Ticket {
        final LotteryQuoteRequest request; final String token; final LotteryPool pool; final long created;
        Ticket(LotteryQuoteRequest request, String token, LotteryPool pool, long created) {
            this.request = request; this.token = token; this.pool = pool; this.created = created;
        }
    }
}
