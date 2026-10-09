package xin.vanilla.sakura.network.packet;

import org.junit.*;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.data.lottery.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class LotteryOriginDispatchTest {
    @After public void reset() { callbacks(new AtomicReference<>()); }
    @Test public void queuedHandlersCaptureOriginalTransportBeforeConnectionChanges() {
        AtomicReference<Object> seen = new AtomicReference<>(); callbacks(seen);
        Context context = new Context(); Object original = context.origin;
        LotteryPoolSyncPacket.handle(new LotteryPoolSyncPacket(Collections.emptyList(), 7), context);
        context.origin = new Object(); context.queued.run(); assertSame(original, seen.get());
        seen.set(null); context = new Context(); original = context.origin;
        LotteryQuotePacket.handle(new LotteryQuotePacket(new LotteryQuote(new LotteryQuoteRequest(
                UUID.randomUUID(), 1, 7, "daily", 1), LotteryQuote.Status.PAYMENT_UNAVAILABLE, 7, 0, "", "")), context);
        context.origin = new Object(); context.queued.run(); assertSame(original, seen.get());
    }
    @Test public void wrongSideOrUnknownTransportDoesNotReachClientCallbacks() {
        AtomicReference<Object> seen = new AtomicReference<>(); callbacks(seen);
        Context context = new Context(); context.client = false;
        LotteryPoolSyncPacket.handle(new LotteryPoolSyncPacket(Collections.emptyList(), 7), context);
        if (context.queued != null) context.queued.run(); assertNull(seen.get());
        context = new Context(); context.origin = null;
        LotteryPoolSyncPacket.handle(new LotteryPoolSyncPacket(Collections.emptyList(), 7), context);
        assertNull(context.queued); assertTrue(context.handled);
    }
    private static void callbacks(AtomicReference<Object> seen) {
        SakuraClientPacketHandlers.register(p -> {}, p -> {}, p -> {}, p -> {}, p -> {}, p -> {},
                (p, origin) -> seen.set(origin), (p, origin) -> seen.set(origin),
                (p, origin) -> seen.set(origin), success -> {});
    }
    private static class Context implements BaniraNetworkContext {
        Object origin = new Object(); Runnable queued; boolean client = true, handled;
        public void enqueueWork(Runnable work) { queued = work; }
        public void markHandled() { handled = true; }
        public boolean isClientSide() { return client; }
        public boolean isServerSide() { return !client; }
        public Object sender() { return null; }
        public Object connectionKey() { return origin; }
    }
}
