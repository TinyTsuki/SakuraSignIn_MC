package xin.vanilla.sakura.network.packet;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.*;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.*;
import xin.vanilla.sakura.network.lottery.LotteryPoolReceiver;
import xin.vanilla.sakura.reward.*;
import xin.vanilla.sakura.util.SakuraUtils;
import java.io.IOException;
import java.util.*;

public final class LotteryPoolSyncPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<LotteryPoolSyncPacket>,
        SplitPacket.SplittableSplitPacket<LotteryPoolSyncPacket> {
    private static final LotteryPoolReceiver SERVER = new LotteryPoolReceiver(System::nanoTime);
    private final long generation;
    private final boolean configurationUpload;
    private final String fragment;
    private List<LotteryPool> pools;

    public LotteryPoolSyncPacket(List<LotteryPool> pools, long generation) {
        configurationUpload = false;
        if (generation < 0) throw new IllegalArgumentException("Invalid lottery revision");
        try {
            RewardConfigDocument document = new RewardConfigDocument();
            document.setLotteryPools(pools);
            fragment = new RewardConfigCodec().encodeDocument(document);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid lottery snapshot", failure);
        }
        LotteryProtocolLimits.json(fragment, LotteryProtocolLimits.MAX_DOCUMENT_BYTES);
        this.generation = generation;
        this.pools = LotteryPools.copy(pools);
        setId(UUID.randomUUID().toString()); setTotal(1); setSort(0);
    }

    public LotteryPoolSyncPacket(RewardConfig configuration, long generation) {
        if (generation < 0) throw new IllegalArgumentException("Invalid reward revision");
        try { fragment = new RewardConfigCodec().encode(configuration); }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid reward snapshot", failure); }
        LotteryProtocolLimits.json(fragment, LotteryProtocolLimits.MAX_UPLOAD_BYTES);
        this.generation = generation; configurationUpload = true;
        setId(UUID.randomUUID().toString()); setTotal(1); setSort(0);
    }

    private LotteryPoolSyncPacket(long generation, boolean upload, String fragment, String id, int total, int sort) {
        configurationUpload = upload;
        this.generation = generation; this.fragment = fragment;
        setId(id); setTotal(total); setSort(sort);
        if (!hasValidHeader()) throw new IllegalArgumentException("Invalid lottery fragment");
    }

    public LotteryPoolSyncPacket(BaniraPacketBuffer buffer) {
        this(readHeader(buffer));
    }

    private LotteryPoolSyncPacket(Header header) {
        this(header.generation, header.upload, header.fragment, header.id, header.total, header.sort);
    }

    private static Header readHeader(BaniraPacketBuffer buffer) {
        String id = buffer.readUtf(36);
        int total = buffer.readInt(), sort = buffer.readInt();
        long generation = buffer.readLong();
        boolean upload = buffer.readBoolean();
        if (total < 1 || total > (upload ? LotteryProtocolLimits.MAX_UPLOAD_PARTS : LotteryProtocolLimits.MAX_PARTS)
                || sort < 0 || sort >= total || generation < 0)
            throw new IllegalArgumentException("Invalid lottery fragment header");
        return new Header(id, total, sort, generation, upload, buffer.readUtf(LotteryProtocolLimits.MAX_FRAGMENT_CHARS));
    }

    public long getGeneration() { return generation; }
    public String getFragment() { return fragment; }
    public boolean isConfigurationUpload() { return configurationUpload; }
    public int maximumBytes() {
        return configurationUpload ? LotteryProtocolLimits.MAX_UPLOAD_BYTES : LotteryProtocolLimits.MAX_DOCUMENT_BYTES;
    }

    public RewardConfig getConfiguration() {
        if (!configurationUpload || getTotal() != 1) throw new IllegalStateException("Incomplete reward upload");
        LotteryProtocolLimits.json(fragment, LotteryProtocolLimits.MAX_UPLOAD_BYTES);
        try { return new RewardConfigCodec().decode(fragment); }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid reward upload", failure); }
    }

    public synchronized List<LotteryPool> getPools() {
        if (pools == null) {
            if (getTotal() != 1) throw new IllegalStateException("Incomplete lottery snapshot");
            pools = decode(fragment);
        }
        return LotteryPools.copy(pools);
    }

    private static List<LotteryPool> decode(String document) {
        LotteryProtocolLimits.json(document, LotteryProtocolLimits.MAX_DOCUMENT_BYTES);
        try {
            RewardConfigDocument decoded = new RewardConfigCodec().decodeDocument(document);
            if (!decoded.getGroups().isEmpty() || !decoded.getPersonalDatePresets().isEmpty())
                throw new IllegalArgumentException("Lottery snapshot contains unrelated reward groups");
            return LotteryPools.copy(decoded.getLotteryPools());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid lottery snapshot", failure);
        }
    }

    public boolean hasValidHeader() {
        try {
            LotteryProtocolLimits.token(getId());
            return generation >= 0 && getTotal() >= 1 && getTotal() <= (configurationUpload
                    ? LotteryProtocolLimits.MAX_UPLOAD_PARTS : LotteryProtocolLimits.MAX_PARTS)
                    && getSort() >= 0 && getSort() < getTotal() && fragment != null
                    && fragment.length() <= LotteryProtocolLimits.MAX_FRAGMENT_CHARS
                    && LotteryProtocolLimits.bytes(fragment) <= LotteryProtocolLimits.MAX_FRAGMENT_BYTES;
        } catch (IllegalArgumentException failure) { return false; }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        if (!hasValidHeader()) throw new IllegalArgumentException("Lottery snapshot must be split before encoding");
        buffer.writeUtf(getId(), 36); buffer.writeInt(getTotal()); buffer.writeInt(getSort());
        buffer.writeLong(generation); buffer.writeBoolean(configurationUpload);
        buffer.writeUtf(fragment, LotteryProtocolLimits.MAX_FRAGMENT_BYTES);
    }

    public static void handle(LotteryPoolSyncPacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            if (packet.configurationUpload) { context.markHandled(); return; }
            Object origin = context.connectionKey();
            if (origin != null) context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet, origin));
        }
        else if (context.isServerSide()) context.enqueueWork(() -> applyServerUpdate(packet, context));
        context.markHandled();
    }

    private static void applyServerUpdate(LotteryPoolSyncPacket packet, BaniraNetworkContext context) {
        ServerPlayer sender = context.senderAs(ServerPlayer.class);
        if (sender == null || sender.server.getPlayerList().getPlayer(sender.getUUID()) != sender) return;
        if (!sender.hasPermissions(CommonConfig.get().permission().permissionEditReward())
                || !packet.configurationUpload && !sender.hasPermissions(SakuraUtils.getRewardPermissionLevel(ERewardRule.LOTTERY_REWARD))
                || packet.generation != RewardConfigManager.getLotteryGeneration()) {
            SERVER.remove(sender.connection);
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender); return;
        }
        try {
            LotteryPoolSyncPacket complete = SERVER.accept(sender.connection, packet);
            if (complete == null) return;
            RewardConfig authoritative = RewardConfigManager.getRewardConfig();
            RewardConfig candidate;
            if (complete.configurationUpload) {
                candidate = RewardConfigManager.mergeUpload(complete.getConfiguration(), authoritative,
                        rule -> sender.hasPermissions(SakuraUtils.getRewardPermissionLevel(rule)));
            } else {
                candidate = RewardConfigManager.deserializeRewardOption(RewardConfigManager.serializeRewardOption(authoritative));
                candidate.setLotteryPools(complete.getPools());
            }
            if (!RewardRuleAddPermissionChecker.canApply(sender, authoritative, candidate)
                    || !RewardAddPermissionChecker.canApply(sender, authoritative, candidate))
                throw new IllegalArgumentException("Lottery reward permission denied");
            if (complete.configurationUpload) RewardConfigManager.replaceRewardConfig(candidate, complete.generation);
            else RewardConfigManager.replaceLotteryPools(candidate.getLotteryPools(), complete.generation);
            for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
                if (complete.configurationUpload) {
                    SakuraNetwork.sendSplitToPlayer(RewardConfigManager.toSyncPacket(player), player);
                    SakuraNetwork.sendSplitToPlayer(SakuraNetwork.personalDatePacket(player), player);
                }
                SakuraNetwork.sendSplitToPlayer(SakuraNetwork.lotteryPoolPacket(player), player);
            }
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(true), sender);
        } catch (IOException | RuntimeException failure) {
            SERVER.remove(sender.connection);
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
        }
    }

    public static void expireServerTransfers() { SERVER.expire(); }
    public static void clearServerTransfers() { SERVER.clear(); }
    public static void removeServerTransfer(Object connection) { SERVER.remove(connection); }

    @Override public List<LotteryPoolSyncPacket> splitPacket() {
        List<LotteryPoolSyncPacket> parts = new ArrayList<>();
        for (int start = 0; start < fragment.length();) {
            int end = Math.min(start + LotteryProtocolLimits.MAX_FRAGMENT_CHARS, fragment.length());
            if (end < fragment.length() && Character.isHighSurrogate(fragment.charAt(end - 1))) end--;
            parts.add(new LotteryPoolSyncPacket(generation, configurationUpload, fragment.substring(start, end), getId(), 1, 0));
            start = end;
        }
        if (parts.size() > (configurationUpload ? LotteryProtocolLimits.MAX_UPLOAD_PARTS : LotteryProtocolLimits.MAX_PARTS))
            throw new IllegalArgumentException("Too many lottery fragments");
        for (int i = 0; i < parts.size(); i++) { parts.get(i).setTotal(parts.size()); parts.get(i).setSort(i); }
        return parts;
    }

    @Override public LotteryPoolSyncPacket mergePackets(List<LotteryPoolSyncPacket> packets) {
        if (packets == null || packets.isEmpty() || packets.size() > LotteryProtocolLimits.MAX_UPLOAD_PARTS)
            throw new IllegalArgumentException("Invalid lottery assembly");
        LotteryPoolSyncPacket first = packets.get(0);
        Map<Integer, LotteryPoolSyncPacket> sorted = new TreeMap<>();
        int bytes = 0;
        for (LotteryPoolSyncPacket part : packets) {
            if (!part.hasValidHeader() || !first.getId().equals(part.getId()) || first.generation != part.generation
                    || first.configurationUpload != part.configurationUpload
                    || part.getTotal() != packets.size() || sorted.put(part.getSort(), part) != null)
                throw new IllegalArgumentException("Mixed lottery assembly");
            int size = LotteryProtocolLimits.bytes(part.fragment);
            if (bytes > first.maximumBytes() - size)
                throw new IllegalArgumentException("Lottery assembly exceeds byte budget");
            bytes += size;
        }
        StringBuilder document = new StringBuilder();
        sorted.values().forEach(part -> document.append(part.fragment));
        LotteryPoolSyncPacket complete;
        if (first.configurationUpload) {
            LotteryProtocolLimits.json(document.toString(), LotteryProtocolLimits.MAX_UPLOAD_BYTES);
            try { complete = new LotteryPoolSyncPacket(new RewardConfigCodec().decode(document.toString()), first.generation); }
            catch (IOException failure) { throw new IllegalArgumentException("Invalid reward upload", failure); }
        } else complete = new LotteryPoolSyncPacket(decode(document.toString()), first.generation);
        complete.setId(first.getId()); return complete;
    }

    @Override public int getChunkSize() { return LotteryProtocolLimits.MAX_FRAGMENT_CHARS; }

    private static final class Header {
        final String id, fragment; final int total, sort; final long generation; final boolean upload;
        Header(String id, int total, int sort, long generation, boolean upload, String fragment) {
            this.upload = upload;
            this.id = id; this.total = total; this.sort = sort; this.generation = generation; this.fragment = fragment;
        }
    }
}
