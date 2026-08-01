package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.data.personaldate.PersonalDateCalendarPolicy;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresets;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.reward.RewardJsonCodec;
import xin.vanilla.sakura.reward.RewardList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端预设独立分包同步，客户端不能通过此包反向修改配置。 */
@Getter
public final class PersonalDatePresetSyncPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<PersonalDatePresetSyncPacket>,
        SplitPacket.SplittableSplitPacket<PersonalDatePresetSyncPacket> {
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_PRESETS_PER_PART = 128;
    private static final int MAX_REWARDS_PER_PRESET = 256;

    private final List<PersonalDatePreset> presets;

    public PersonalDatePresetSyncPacket(List<PersonalDatePreset> presets) {
        this.presets = PersonalDatePresets.copy(presets);
    }

    public PersonalDatePresetSyncPacket(BaniraPacketBuffer buffer) {
        super(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PRESETS_PER_PART) {
            throw new IllegalArgumentException("Invalid personal date preset count: " + count);
        }
        presets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf();
            String displayName = buffer.readUtf();
            PersonalDateRecurrence recurrence = buffer.readEnum(PersonalDateRecurrence.class);
            PersonalDateCalendarPolicy policy = buffer.readEnum(PersonalDateCalendarPolicy.class);
            int maxSlots = buffer.readVarInt();
            PersonalDateDeliveryMode delivery = buffer.readEnum(PersonalDateDeliveryMode.class);
            int before = buffer.readVarInt();
            int after = buffer.readVarInt();
            int rewardCount = buffer.readVarInt();
            if (rewardCount < 0 || rewardCount > MAX_REWARDS_PER_PRESET) {
                throw new IllegalArgumentException("Invalid personal date reward count: " + rewardCount);
            }
            RewardList rewards = new RewardList();
            for (int rewardIndex = 0; rewardIndex < rewardCount; rewardIndex++) {
                rewards.add(RewardJsonCodec.decode(
                        new com.google.gson.JsonParser().parse(buffer.readUtf())));
            }
            presets.add(new PersonalDatePreset(id, displayName, recurrence, policy,
                    maxSlots, delivery, before, after, rewards));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        super.toBytes(buffer);
        buffer.writeVarInt(presets.size());
        for (PersonalDatePreset preset : presets) {
            buffer.writeUtf(preset.getId());
            buffer.writeUtf(preset.getDisplayName());
            buffer.writeEnum(preset.getRecurrence());
            buffer.writeEnum(preset.getCalendarPolicy());
            buffer.writeVarInt(preset.getMaxDateSlots());
            buffer.writeEnum(preset.getDeliveryMode());
            buffer.writeVarInt(preset.getValidBeforeDays());
            buffer.writeVarInt(preset.getValidAfterDays());
            buffer.writeVarInt(preset.getRewards().size());
            preset.getRewards().forEach(reward ->
                    buffer.writeUtf(RewardJsonCodec.encode(reward).toString()));
        }
    }

    public static void handle(PersonalDatePresetSyncPacket packet, BaniraNetworkContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet));
        }
        ctx.markHandled();
    }

    @Override
    public List<PersonalDatePresetSyncPacket> splitPacket() {
        if (presets.isEmpty()) {
            List<PersonalDatePresetSyncPacket> result = Collections.singletonList(
                    new PersonalDatePresetSyncPacket(Collections.emptyList()));
            initializeParts(result);
            return result;
        }
        List<PersonalDatePresetSyncPacket> result = new ArrayList<>();
        for (int start = 0; start < presets.size(); start += CHUNK_SIZE) {
            result.add(new PersonalDatePresetSyncPacket(
                    presets.subList(start, Math.min(start + CHUNK_SIZE, presets.size()))));
        }
        initializeParts(result);
        return result;
    }

    @Override
    public PersonalDatePresetSyncPacket mergePackets(List<PersonalDatePresetSyncPacket> packets) {
        List<PersonalDatePreset> merged = new ArrayList<>();
        packets.forEach(packet -> merged.addAll(packet.presets));
        return new PersonalDatePresetSyncPacket(merged);
    }

    @Override
    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    private void initializeParts(List<PersonalDatePresetSyncPacket> packets) {
        for (int i = 0; i < packets.size(); i++) {
            PersonalDatePresetSyncPacket packet = packets.get(i);
            packet.setId(getId());
            packet.setSort(i);
            packet.setTotal(packets.size());
        }
    }
}
