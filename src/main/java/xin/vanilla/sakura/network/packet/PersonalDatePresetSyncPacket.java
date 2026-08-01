package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresets;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.calendar.CalendarDescriptor;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.calendar.SakuraCalendars;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresetValidator;
import xin.vanilla.sakura.reward.RewardAddPermissionChecker;
import xin.vanilla.sakura.reward.RewardRuleAddPermissionChecker;
import xin.vanilla.sakura.reward.RewardJsonCodec;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 个性化日期预设使用独立分包同步，并由服务端权威校验客户端修改。 */
@Getter
public final class PersonalDatePresetSyncPacket extends SplitPacket implements INetworkPacket,
        SplitPacket.MergeableSplitPacket<PersonalDatePresetSyncPacket>,
        SplitPacket.SplittableSplitPacket<PersonalDatePresetSyncPacket> {
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_PRESETS_PER_PART = 128;
    private static final int MAX_REWARDS_PER_PRESET = 256;

    private final List<PersonalDatePreset> presets;
    private final List<CalendarDescriptor> calendars;

    public PersonalDatePresetSyncPacket(List<PersonalDatePreset> presets) {
        this(presets, Collections.emptyList());
    }

    public PersonalDatePresetSyncPacket(List<PersonalDatePreset> presets,
                                        List<CalendarDescriptor> calendars) {
        this.presets = PersonalDatePresets.copy(presets);
        this.calendars = calendars == null
                ? new ArrayList<>() : new ArrayList<>(calendars);
    }

    public PersonalDatePresetSyncPacket(BaniraPacketBuffer buffer) {
        super(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PRESETS_PER_PART) {
            throw new IllegalArgumentException("Invalid personal date preset count: " + count);
        }
        presets = new ArrayList<>();
        calendars = new ArrayList<>();
        int calendarDescriptorCount = buffer.readVarInt();
        if (calendarDescriptorCount < 0 || calendarDescriptorCount > 128) {
            throw new IllegalArgumentException("Invalid calendar descriptor count: "
                    + calendarDescriptorCount);
        }
        for (int index = 0; index < calendarDescriptorCount; index++) {
            calendars.add(new CalendarDescriptor(buffer.readUtf(), buffer.readUtf()));
        }
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf();
            String displayName = buffer.readUtf();
            PersonalDateRecurrence recurrence = buffer.readEnum(PersonalDateRecurrence.class);
            int calendarCount = buffer.readVarInt();
            if (calendarCount < 1 || calendarCount > 32) {
                throw new IllegalArgumentException("Invalid personal date calendar count: " + calendarCount);
            }
            List<String> calendarIds = new ArrayList<>();
            for (int calendarIndex = 0; calendarIndex < calendarCount; calendarIndex++) {
                calendarIds.add(buffer.readUtf());
            }
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
            presets.add(new PersonalDatePreset(id, displayName, recurrence, calendarIds,
                    maxSlots, delivery, before, after, rewards));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        super.toBytes(buffer);
        buffer.writeVarInt(presets.size());
        buffer.writeVarInt(calendars.size());
        calendars.forEach(calendar -> {
            buffer.writeUtf(calendar.getId());
            buffer.writeUtf(calendar.getDisplayNameKey());
        });
        for (PersonalDatePreset preset : presets) {
            buffer.writeUtf(preset.getId());
            buffer.writeUtf(preset.getDisplayName());
            buffer.writeEnum(preset.getRecurrence());
            buffer.writeVarInt(preset.getCalendarIds().size());
            preset.getCalendarIds().forEach(buffer::writeUtf);
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
        } else {
            ctx.enqueueWork(() -> applyServerUpdate(packet, ctx));
        }
        ctx.markHandled();
    }

    private static void applyServerUpdate(PersonalDatePresetSyncPacket packet,
                                          BaniraNetworkContext context) {
        ServerPlayerEntity sender = context.senderAs(ServerPlayerEntity.class);
        if (sender == null || !sender.hasPermissions(
                CommonConfig.get().permission().permissionEditReward())
                || !sender.hasPermissions(SakuraUtils.getRewardPermissionLevel(
                ERewardRule.PERSONAL_DATE_REWARD))) {
            if (sender != null) {
                BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
            }
            return;
        }
        try {
            if (packet.presets.size() > MAX_PRESETS_PER_PART) {
                throw new IllegalArgumentException("Too many personal date presets");
            }
            Set<String> presetIds = new HashSet<>();
            if (packet.presets.stream().anyMatch(preset ->
                    !PersonalDatePresetValidator.validate(preset).isEmpty()
                            || !presetIds.add(preset.getId())
                            || preset.getCalendarIds().stream().anyMatch(id ->
                            !SakuraCalendars.get().find(id).isPresent()))) {
                throw new IllegalArgumentException("Invalid personal date preset update");
            }
            RewardConfig authoritative = RewardConfigManager.getRewardConfig();
            RewardConfig candidate = RewardConfigManager.deserializeRewardOption(
                    RewardConfigManager.serializeRewardOption(authoritative));
            candidate.setPersonalDatePresets(PersonalDatePresets.copy(packet.presets));
            if (!RewardRuleAddPermissionChecker.canApply(sender, authoritative, candidate)
                    || !RewardAddPermissionChecker.canApply(sender, authoritative, candidate)) {
                throw new IllegalArgumentException("Personal date reward permission denied");
            }
            RewardConfigManager.backupRewardOption(false);
            authoritative.setPersonalDatePresets(PersonalDatePresets.copy(packet.presets));
            RewardConfigManager.saveRewardOption();
            for (ServerPlayerEntity player : sender.server.getPlayerList().getPlayers()) {
                SakuraNetwork.sendSplitToPlayer(SakuraNetwork.personalDatePacket(player), player);
            }
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(true), sender);
        } catch (RuntimeException failure) {
            BaniraNetwork.sendToPlayer(new RewardOptionDataReceivedNotice(false), sender);
        }
    }

    @Override
    public List<PersonalDatePresetSyncPacket> splitPacket() {
        if (presets.isEmpty()) {
            List<PersonalDatePresetSyncPacket> result = Collections.singletonList(
                    new PersonalDatePresetSyncPacket(Collections.emptyList(), calendars));
            initializeParts(result);
            return result;
        }
        List<PersonalDatePresetSyncPacket> result = new ArrayList<>();
        for (int start = 0; start < presets.size(); start += CHUNK_SIZE) {
            result.add(new PersonalDatePresetSyncPacket(
                    presets.subList(start, Math.min(start + CHUNK_SIZE, presets.size())),
                    start == 0 ? calendars : Collections.emptyList()));
        }
        initializeParts(result);
        return result;
    }

    @Override
    public PersonalDatePresetSyncPacket mergePackets(List<PersonalDatePresetSyncPacket> packets) {
        List<PersonalDatePreset> merged = new ArrayList<>();
        List<CalendarDescriptor> mergedCalendars = new ArrayList<>();
        packets.forEach(packet -> merged.addAll(packet.presets));
        packets.forEach(packet -> mergedCalendars.addAll(packet.calendars));
        return new PersonalDatePresetSyncPacket(merged, mergedCalendars);
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
