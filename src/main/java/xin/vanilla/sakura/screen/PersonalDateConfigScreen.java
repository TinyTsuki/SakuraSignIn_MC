package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.CustomPlayerConfigEditScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.CollapsiblePanelWidget;
import xin.vanilla.banira.client.gui.widget.IWidget;
import xin.vanilla.banira.client.gui.widget.LabelWidget;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.PersonalDateSlotUpdatePacket;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 编辑服务器定义的玩家个性化日期。
 */
public final class PersonalDateConfigScreen extends xin.vanilla.banira.client.gui.PlayerConfigScreen {
    private final List<PlayerPersonalDateSlot> slots = new ArrayList<>();

    public PersonalDateConfigScreen(Screen parent) {
        super(SakuraSignIn.MODID, SakuraComponent.get().transClient("word", "personal_date_config"),
                parent, null, BaniraThemes.seasonFor(SakuraSignIn.MODID));
        loadSlots();
    }

    @Override
    protected void buildPlayerConfig(CollapsiblePanelWidget root) {
        addBaniraConfigRow(root);
        List<PersonalDatePreset> presets = activePresets();
        if (presets.isEmpty()) {
            addEmptyRow(root);
            return;
        }
        for (PersonalDatePreset preset : presets) {
            CollapsiblePanelWidget section = addPlayerSection(root,
                    "personal_date_" + preset.getId(),
                    SakuraComponent.get().literal(preset.getDisplayName()), null);
            for (int index = 0; index < preset.getMaxDateSlots(); index++) {
                addDateRow(section, preset, index);
            }
            section.refreshLayout();
        }
    }

    private void addBaniraConfigRow(CollapsiblePanelWidget root) {
        double width = root.getContentWidth();
        ButtonWidget button = new ButtonWidget(this);
        button.id("banira_player_config");
        button.bounds(new ScreenCoordinate(0, 0, width, ROW_HEIGHT));
        Component title = SakuraComponent.get().transClient("word", "banira_player_config");
        button.text(title);
        button.onClick(clicked -> openBaniraPlayerConfig());
        addPlayerRow(root, button, ROW_HEIGHT, null, null, title, null,
                "banira", "common", "player config");
    }

    private void addEmptyRow(CollapsiblePanelWidget root) {
        double width = root.getContentWidth();
        LabelWidget label = new LabelWidget(this);
        label.id("personal_date_no_presets");
        label.bounds(new ScreenCoordinate(0, 0, width, ROW_HEIGHT));
        Component title = SakuraComponent.get().transClient("word", "personal_date_no_presets");
        label.text(Text.from(title));
        addPlayerRow(root, label, ROW_HEIGHT, label, null, title, null,
                "personal date", "preset");
    }

    private void addDateRow(CollapsiblePanelWidget section, PersonalDatePreset preset, int index) {
        double width = section.getContentWidth();
        PlayerPersonalDateSlot slot = findOrCreate(preset, index);
        EntryRowWidget row = new EntryRowWidget(this);
        row.id("personal_date_" + preset.getId() + "_" + index);
        row.bounds(new ScreenCoordinate(0, 0, width, ROW_HEIGHT));

        ButtonWidget calendar = new ButtonWidget(this);
        calendar.id(row.id() + "_calendar");
        calendar.bounds(new ScreenCoordinate(0, 0, width / 2 - 2, ROW_HEIGHT));
        calendar.text(SakuraComponent.get().literal(calendarName(slot.getCalendarId())));
        calendar.onClick(clicked -> cycleCalendar(preset, slot));

        String dateText = preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                ? String.valueOf(slot.getDay()) : slot.getMonth() + "/" + slot.getDay();
        ButtonWidget date = new ButtonWidget(this);
        date.id(row.id() + "_date");
        date.bounds(new ScreenCoordinate(width / 2 + 2, 0, width / 2 - 2, ROW_HEIGHT));
        date.text(SakuraComponent.get().literal(dateText));
        date.onClick(clicked -> openDateInput(preset, slot));

        row.addChild(calendar);
        row.addChild(date);
        Component title = SakuraComponent.get().literal(preset.getDisplayName() + " " + (index + 1));
        addPlayerRow(section, row, ROW_HEIGHT, null, null, title, null,
                preset.getId(), slot.getCalendarId(), calendarName(slot.getCalendarId()), dateText);
    }

    private void loadSlots() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        SakuraPlayerData.get(player).getPersonalDateSlots().stream()
                .map(slot -> PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT()))
                .forEach(slots::add);
    }

    private List<PersonalDatePreset> activePresets() {
        return RewardConfigManager.getRewardConfig().getPersonalDatePresets().stream()
                .filter(preset -> preset != null && preset.getCalendarIds() != null
                        && !preset.getCalendarIds().isEmpty())
                .sorted(Comparator.comparing(PersonalDatePreset::getId))
                .collect(Collectors.toList());
    }

    private PlayerPersonalDateSlot findOrCreate(PersonalDatePreset preset, int index) {
        return slots.stream().filter(slot -> preset.getId().equals(slot.getPresetId())
                        && slot.getSlotIndex() == index).findFirst()
                .orElseGet(() -> {
                    PlayerPersonalDateSlot slot = new PlayerPersonalDateSlot(
                            preset.getId(), index, preset.getCalendarIds().get(0),
                            preset.getRecurrence() == PersonalDateRecurrence.MONTHLY ? 0 : 1,
                            1, "");
                    slots.add(slot);
                    return slot;
                });
    }

    private void cycleCalendar(PersonalDatePreset preset, PlayerPersonalDateSlot slot) {
        List<String> ids = preset.getCalendarIds().stream()
                .filter(SakuraClientState.getCalendarNames()::containsKey)
                .collect(Collectors.toList());
        if (ids.isEmpty()) ids = preset.getCalendarIds();
        int current = ids.indexOf(slot.getCalendarId());
        slot.setCalendarId(ids.get((current + 1 + ids.size()) % ids.size()));
        refreshWidget();
    }

    private void openDateInput(PersonalDatePreset preset, PlayerPersonalDateSlot slot) {
        boolean monthly = preset.getRecurrence() == PersonalDateRecurrence.MONTHLY;
        String current = monthly ? String.valueOf(slot.getDay()) : slot.getMonth() + "/" + slot.getDay();
        Minecraft.getInstance().setScreen(new StringInputScreen(this,
                Text.trans(SakuraSignIn.MODID, monthly
                        ? "word.sakura_sign_in.personal_date_day"
                        : "word.sakura_sign_in.personal_date_month_day"),
                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.personal_date_value_hint"),
                monthly ? "[0-9]{1,3}" : "[0-9]{1,2}/[0-9]{1,3}", current,
                values -> {
                    String[] parts = values.get(0).split("/");
                    if (monthly) {
                        slot.setMonth(0);
                        slot.setDay(Integer.parseInt(parts[0]));
                    } else {
                        slot.setMonth(Integer.parseInt(parts[0]));
                        slot.setDay(Integer.parseInt(parts[1]));
                    }
                    refreshWidget();
                }));
    }

    @Override
    protected void savePlayerConfig() {
        List<PlayerPersonalDateSlot> selected = slots.stream()
                .filter(slot -> slot.getDay() > 0)
                .collect(Collectors.toList());
        SakuraNetwork.sendToServer(new PersonalDateSlotUpdatePacket(selected));
        SakuraClientNotifications.success(SakuraComponent.get().transClient(
                "word", "personal_date_save_success"), SakuraNotificationTypes.REWARD);
    }

    private void openBaniraPlayerConfig() {
        Minecraft.getInstance().setScreen(new CustomPlayerConfigEditScreen(
                new CustomPlayerConfigEditScreen.Args().parentScreen(this)
                        .season(BaniraThemes.seasonFor(SakuraSignIn.MODID))));
    }

    private String calendarName(String id) {
        String key = SakuraClientState.getCalendarNames().get(id);
        if (key == null) return id;
        String translated = I18n.get(key);
        if (!translated.equals(key)) return translated;
        return key.indexOf('.') < 0 ? key : id;
    }

    private static final class EntryRowWidget extends BaseWidget {
        private EntryRowWidget(BaniraScreen screen) {
            super(screen);
        }

        @Override
        public double effectiveHeight() {
            double maxBottom = 0;
            for (IWidget child : children()) {
                if (child == null || !child.visible() || child.bounds() == null) continue;
                maxBottom = Math.max(maxBottom, child.bounds().y() + child.effectiveHeight());
            }
            return maxBottom > 0 ? maxBottom : (bounds() != null ? bounds().height() : 0);
        }

        @Override
        protected boolean onMouseClick(MouseEvent event) {
            return true;
        }

        @Override
        public void render(PoseStack stack, float partialTicks) {
            if (visible()) renderChildren(stack, partialTicks);
        }
    }
}
