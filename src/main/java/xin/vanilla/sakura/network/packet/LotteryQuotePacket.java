package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.lottery.LotteryPacketFields;

@Getter
public final class LotteryQuotePacket implements INetworkPacket {
    private final LotteryQuote quote;
    public LotteryQuotePacket(LotteryQuote quote) {
        if (quote == null) throw new IllegalArgumentException("Lottery quote required");
        this.quote = quote;
    }
    public LotteryQuotePacket(BaniraPacketBuffer buffer) {
        this(new LotteryQuote(LotteryPacketFields.readRequest(buffer), buffer.readEnum(LotteryQuote.Status.class),
                buffer.readLong(), buffer.readInt(), buffer.readUtf(4096), buffer.readUtf(36)));
    }
    public void toBytes(BaniraPacketBuffer buffer) {
        LotteryPacketFields.writeRequest(buffer, quote.getRequest()); buffer.writeEnum(quote.getStatus());
        buffer.writeLong(quote.getGeneration()); buffer.writeInt(quote.getAvailableCount());
        buffer.writeUtf(quote.getPaymentSummary(), 16_384); buffer.writeUtf(quote.getQuoteToken(), 36);
    }
    public static void handle(LotteryQuotePacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            Object origin = context.connectionKey();
            if (origin != null) context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet, origin));
        }
        context.markHandled();
    }
}
