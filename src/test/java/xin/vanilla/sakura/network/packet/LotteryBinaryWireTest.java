package xin.vanilla.sakura.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.Test;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class LotteryBinaryWireTest {
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test public void nativeBinaryRoundTripsEveryOperationHeaderAndQuoteResponse() {
        LotteryQuoteRequest request = new LotteryQuoteRequest(SESSION, 5, 7, "daily", -1);
        try (Wire wire = new Wire()) {
            new LotteryQuoteRequestPacket(request).toBytes(wire);
            assertEquals(request, new LotteryQuoteRequestPacket(wire).getRequest());
        }
        LotteryQuote quote = new LotteryQuote(request, LotteryQuote.Status.READY, 7, 100, "paper", TOKEN);
        try (Wire wire = new Wire()) {
            new LotteryQuotePacket(quote).toBytes(wire);
            assertEquals(quote, new LotteryQuotePacket(wire).getQuote());
        }
        LotteryDrawRequest draw = new LotteryDrawRequest(SESSION, 6, 7, "daily", -1, TOKEN);
        try (Wire wire = new Wire()) {
            new LotteryDrawRequestPacket(draw).toBytes(wire);
            assertEquals(draw, new LotteryDrawRequestPacket(wire).getRequest());
        }
        try (Wire wire = new Wire()) {
            new LotteryClaimPacket(draw.getOperationId(), TOKEN).toBytes(wire);
            LotteryClaimPacket decoded = new LotteryClaimPacket(wire);
            assertEquals(draw.getOperationId(), decoded.getOperationId()); assertEquals(TOKEN, decoded.getToken());
        }
    }

    @Test public void largeUnicodeDocumentsSplitIntoSmallRealByteBuffers() {
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 1, 0, new RewardList());
        StringBuilder text = new StringBuilder(); for (int i = 0; i < 1024; i++) text.append("\ud83c\udf38");
        pool.setDescription(text.toString());
        LotteryPoolSyncPacket source = new LotteryPoolSyncPacket(Collections.singletonList(pool), 7);
        List<LotteryPoolSyncPacket> parts = new ArrayList<>();
        for (LotteryPoolSyncPacket part : source.splitPacket()) try (Wire wire = new Wire()) {
            part.toBytes(wire); assertTrue(wire.buffer.readableBytes() <= 8320);
            parts.add(new LotteryPoolSyncPacket(wire));
        }
        assertEquals(source.getPools(), source.mergePackets(parts).getPools());
    }

    @Test public void truncatedInvalidAndOversizedBinaryRequestsReject() {
        try (Wire wire = new Wire()) {
            wire.writeUuid(SESSION); wire.writeLong(1);
            assertThrows(RuntimeException.class, () -> new LotteryQuoteRequestPacket(wire));
        }
        try (Wire wire = new Wire()) {
            wire.writeUuid(SESSION); wire.writeLong(1); wire.writeLong(0);
            wire.writeUtf("daily"); wire.writeInt(-2);
            assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequestPacket(wire));
        }
        try (Wire wire = new Wire()) {
            new LotteryQuoteRequestPacket(new LotteryQuoteRequest(SESSION, 1, 7, "daily", 1)).toBytes(wire);
            wire.writeVarInt(99);
            assertThrows(RuntimeException.class, () -> new LotteryQuotePacket(wire));
        }
    }

    @Test public void hiddenWeightsAreNotLeakedOrMutatedByReveal() {
        Reward reward = Reward.getDefault().setProbability(new BigDecimal("7"));
        LotteryRevealPacket packet = new LotteryRevealPacket(SESSION + "-1", TOKEN, "Daily",
                Collections.singletonList(reward), Collections.singletonList(reward), LotteryPreviewMode.ITEMS_ONLY);
        try (Wire wire = new Wire()) {
            packet.toBytes(wire); LotteryRevealPacket decoded = new LotteryRevealPacket(wire);
            assertEquals(BigDecimal.ONE, decoded.getWinner().getProbability());
            assertEquals(BigDecimal.ONE, decoded.getPreview().get(0).getProbability());
        }
        assertEquals(new BigDecimal("7"), reward.getProbability());
        packet.getWinners().get(0).setProbability(BigDecimal.TEN);
        assertEquals(BigDecimal.ONE, packet.getWinner().getProbability());
    }

    static final class Wire implements BaniraPacketBuffer, AutoCloseable {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        public int readVarInt() { return buffer.readVarInt(); }
        public void writeVarInt(int value) { buffer.writeVarInt(value); }
        public String readUtf() { return buffer.readUtf(); }
        public String readUtf(int max) { return buffer.readUtf(max); }
        public void writeUtf(String value) { buffer.writeUtf(value); }
        public void writeUtf(String value, int max) { buffer.writeUtf(value, max); }
        public int readInt() { return buffer.readInt(); }
        public void writeInt(int value) { buffer.writeInt(value); }
        public long readLong() { return buffer.readLong(); }
        public void writeLong(long value) { buffer.writeLong(value); }
        public boolean readBoolean() { return buffer.readBoolean(); }
        public void writeBoolean(boolean value) { buffer.writeBoolean(value); }
        public byte readByte() { return buffer.readByte(); }
        public void writeByte(int value) { buffer.writeByte(value); }
        public double readDouble() { return buffer.readDouble(); }
        public void writeDouble(double value) { buffer.writeDouble(value); }
        public UUID readUuid() { return buffer.readUUID(); }
        public void writeUuid(UUID value) { buffer.writeUUID(value); }
        public <T extends Enum<T>> T readEnum(Class<T> type) { return buffer.readEnum(type); }
        public void writeEnum(Enum<?> value) { buffer.writeEnum(value); }
        public BaniraIdentifier readIdentifier() { throw new UnsupportedOperationException(); }
        public void writeIdentifier(BaniraIdentifier value) { throw new UnsupportedOperationException(); }
        public void close() { buffer.release(); }
    }
}
