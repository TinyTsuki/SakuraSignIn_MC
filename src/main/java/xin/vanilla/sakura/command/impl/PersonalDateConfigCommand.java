package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PersonalDateSelectionResult;
import xin.vanilla.sakura.data.personaldate.PersonalDateSelectionService;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.message.SakuraMessages;

import java.util.ArrayList;
import java.util.List;

/** 未安装客户端的玩家也可通过指令维护自己的个性化日期。 */
final class PersonalDateConfigCommand {
    private static final PersonalDateSelectionService SERVICE = new PersonalDateSelectionService();

    private PersonalDateConfigCommand() {
    }

    static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("personalDate")
                .executes(PersonalDateConfigCommand::list)
                .then(Commands.literal("list").executes(PersonalDateConfigCommand::list))
                .then(Commands.literal("set")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    presets().forEach(preset -> builder.suggest(preset.getId()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 16))
                                        .then(Commands.argument("calendar", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    PersonalDatePreset preset = preset(StringArgumentType
                                                            .getString(context, "preset"));
                                                    if (preset != null) {
                                                        preset.getCalendarIds().forEach(builder::suggest);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("date", StringArgumentType.word())
                                                        .suggests((context, builder) -> builder
                                                                .suggest("7-12").suggest("12").buildFuture())
                                                        .executes(PersonalDateConfigCommand::set))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    presets().forEach(preset -> builder.suggest(preset.getId()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 16))
                                        .executes(PersonalDateConfigCommand::clear))));
    }

    private static int list(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        IPlayerSignInData data = SakuraPlayerData.get(player);
        SakuraMessages.send(player, SakuraComponent.get().trans(player,
                "word", "personal_date_command_header"));
        for (PlayerPersonalDateSlot slot : data.getPersonalDateSlots()) {
            if (slot.getMonth() <= 0 && slot.getDay() <= 0) {
                continue;
            }
            String date = slot.getMonth() > 0
                    ? slot.getMonth() + "-" + slot.getDay() : String.valueOf(slot.getDay());
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "format",
                    "personal_date_command_line", slot.getPresetId(), slot.getSlotIndex() + 1,
                    slot.getCalendarId(), date));
        }
        return 1;
    }

    private static int set(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        String presetId = StringArgumentType.getString(context, "preset");
        PersonalDatePreset preset = preset(presetId);
        int slotIndex = IntegerArgumentType.getInteger(context, "slot") - 1;
        String calendar = StringArgumentType.getString(context, "calendar");
        int[] date = parseDate(preset, StringArgumentType.getString(context, "date"));
        if (preset == null || slotIndex >= preset.getMaxDateSlots() || date == null) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player,
                    "word", "personal_date_command_invalid"));
            return 0;
        }
        List<PlayerPersonalDateSlot> candidates = without(
                SakuraPlayerData.get(player).getPersonalDateSlots(), presetId, slotIndex);
        candidates.add(new PlayerPersonalDateSlot(presetId, slotIndex, calendar,
                date[0], date[1], ""));
        return apply(player, candidates, "personal_date_command_saved");
    }

    private static int clear(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        String presetId = StringArgumentType.getString(context, "preset");
        int slotIndex = IntegerArgumentType.getInteger(context, "slot") - 1;
        return apply(player, without(SakuraPlayerData.get(player).getPersonalDateSlots(),
                presetId, slotIndex), "personal_date_command_cleared");
    }

    private static int apply(ServerPlayerEntity player, List<PlayerPersonalDateSlot> candidates,
                             String successKey) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        PersonalDateSelectionResult result = SERVICE.replace(
                presets(), data.getPersonalDateSlots(), candidates);
        if (!result.isSuccess()) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player,
                    "word", "personal_date_command_invalid"));
            return 0;
        }
        data.setPersonalDateSlots(result.getSlots());
        SakuraPlayerData.saveAndSync(player);
        SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", successKey));
        return 1;
    }

    private static List<PlayerPersonalDateSlot> without(List<PlayerPersonalDateSlot> source,
                                                         String presetId, int slotIndex) {
        List<PlayerPersonalDateSlot> result = new ArrayList<>();
        for (PlayerPersonalDateSlot slot : source) {
            if (!presetId.equals(slot.getPresetId()) || slotIndex != slot.getSlotIndex()) {
                result.add(PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT()));
            }
        }
        return result;
    }

    private static int[] parseDate(PersonalDatePreset preset, String value) {
        if (preset == null || value == null) {
            return null;
        }
        try {
            String[] parts = value.split("-");
            if (preset.getRecurrence() == PersonalDateRecurrence.MONTHLY && parts.length == 1) {
                return new int[]{0, Integer.parseInt(parts[0])};
            }
            return parts.length == 2
                    ? new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])} : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static PersonalDatePreset preset(String id) {
        return presets().stream().filter(value -> value.getId().equals(id)).findFirst().orElse(null);
    }

    private static List<PersonalDatePreset> presets() {
        return RewardConfigManager.getRewardConfig().getPersonalDatePresets();
    }
}
