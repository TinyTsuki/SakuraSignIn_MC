package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.personaldate.PersonalDateSelectionResult;
import xin.vanilla.sakura.data.personaldate.PersonalDateSelectionService;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;

import java.util.ArrayList;
import java.util.List;

/** 玩家只上传日期值，领取游标不会进入客户端候选负载。 */
@Getter
public final class PersonalDateSlotUpdatePacket implements INetworkPacket {
    private static final int MAX_SLOTS = 2048;
    private static final PersonalDateSelectionService SERVICE =
            new PersonalDateSelectionService();

    private final List<PlayerPersonalDateSlot> slots;

    public PersonalDateSlotUpdatePacket(List<PlayerPersonalDateSlot> slots) {
        this.slots = withoutCursors(slots);
    }

    public PersonalDateSlotUpdatePacket(BaniraPacketBuffer buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_SLOTS) {
            throw new IllegalArgumentException("Invalid personal date slot count: " + count);
        }
        slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(new PlayerPersonalDateSlot(
                    buffer.readUtf(), buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt(), ""));
        }
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeVarInt(slots.size());
        for (PlayerPersonalDateSlot slot : slots) {
            buffer.writeUtf(slot.getPresetId());
            buffer.writeVarInt(slot.getSlotIndex());
            buffer.writeUtf(slot.getCalendarId());
            buffer.writeVarInt(slot.getMonth());
            buffer.writeVarInt(slot.getDay());
        }
    }

    public static void handle(PersonalDateSlotUpdatePacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.senderAs(ServerPlayerEntity.class);
            if (sender == null) {
                return;
            }
            IPlayerSignInData data = SakuraPlayerData.get(sender);
            PersonalDateSelectionResult result = SERVICE.replace(
                    RewardConfigManager.getRewardConfig().getPersonalDatePresets(),
                    data.getPersonalDateSlots(), packet.slots);
            if (result.isSuccess()) {
                data.setPersonalDateSlots(result.getSlots());
                SakuraPlayerData.saveAndSync(sender);
            } else {
                SakuraPlayerData.sync(sender);
            }
        });
        ctx.markHandled();
    }

    private static List<PlayerPersonalDateSlot> withoutCursors(
            List<PlayerPersonalDateSlot> source) {
        List<PlayerPersonalDateSlot> result = new ArrayList<>();
        if (source != null) {
            source.stream().filter(slot -> slot != null).forEach(slot ->
                    result.add(new PlayerPersonalDateSlot(
                            slot.getPresetId(), slot.getSlotIndex(), slot.getCalendarId(),
                            slot.getMonth(), slot.getDay(), "")));
        }
        return result;
    }
}
