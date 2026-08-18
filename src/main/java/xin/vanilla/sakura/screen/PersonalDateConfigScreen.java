package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.CustomPlayerConfigEditScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
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

/** Player-facing editor for server-defined personal date slots. */
public final class PersonalDateConfigScreen extends BaniraScreen {
    private static final int CONTENT_TOP = 44;
    private static final int CONTENT_BOTTOM_MARGIN = 38;
    private static final int ROW_STEP = 24;
    private final List<PlayerPersonalDateSlot> slots = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int contentOffset;
    private int contentHeight;

    public PersonalDateConfigScreen(Screen parent) {
        super(SakuraComponent.get().transClient("word", "personal_date_config"));
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
        loadSlots();
    }

    @Override
    protected void initWidgets() {
        rows.clear();
        int panelWidth = Math.min(620, width - 40);
        int panelX = (width - panelWidth) / 2;
        List<PersonalDatePreset> presets = activePresets();
        contentHeight = presets.stream().mapToInt(preset ->
                18 + preset.getMaxDateSlots() * ROW_STEP + 8).sum();
        contentOffset = Math.max(0, Math.min(contentOffset, maxContentOffset()));
        int y = CONTENT_TOP - contentOffset;
        for (PersonalDatePreset preset : presets) {
            rows.add(new Row(y, preset.getDisplayName()));
            y += 18;
            for (int index = 0; index < preset.getMaxDateSlots(); index++) {
                PlayerPersonalDateSlot slot = findOrCreate(preset, index);
                ButtonWidget calendar = contentButton(panelX + 12, y,
                        panelWidth / 2 - 18, 20,
                        calendarName(slot.getCalendarId()),
                        clicked -> cycleCalendar(preset, slot));
                String dateText = preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                        ? String.valueOf(slot.getDay())
                        : slot.getMonth() + "/" + slot.getDay();
                ButtonWidget date = contentButton(panelX + panelWidth / 2, y,
                        panelWidth / 2 - 12, 20, dateText,
                        clicked -> openDateInput(preset, slot));
                rows.get(rows.size() - 1).buttons.add(calendar);
                rows.get(rows.size() - 1).buttons.add(date);
                y += 24;
            }
            y += 8;
        }

        int footerY = height - 30;
        button(panelX, footerY, panelWidth / 3 - 4, 20,
                I18n.get("word.sakura_sign_in.banira_player_config"),
                clicked -> openBaniraPlayerConfig());
        button(panelX + panelWidth / 3, footerY, panelWidth / 3 - 4, 20,
                I18n.get("word.sakura_sign_in.save"), clicked -> save());
        button(panelX + panelWidth * 2 / 3, footerY, panelWidth / 3, 20,
                I18n.get("word.sakura_sign_in.close"), clicked -> onClose());
    }

    @Override
    protected void onRender(PoseStack stack, float partialTicks) {
        int panelWidth = Math.min(620, width - 40);
        int panelX = (width - panelWidth) / 2;
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack)
                .type(ShapeDrawArgs.ShapeType.RECT)
                .color(getEffectiveTheme().panelBg())
                .rect(new ShapeDrawArgs.RectParams().x(panelX - 8).y(20)
                        .width(panelWidth + 16).height(height - 24)
                        .radius(8).border(0)));
        String title = I18n.get("word.sakura_sign_in.personal_date_config");
        font.draw(stack, title, width / 2.0F - font.width(title) / 2.0F,
                28, getEffectiveTheme().buttonText());
        if (rows.isEmpty()) {
            String empty = I18n.get("word.sakura_sign_in.personal_date_no_presets");
            font.draw(stack, empty, width / 2.0F - font.width(empty) / 2.0F,
                    height / 2.0F, getEffectiveTheme().buttonText());
        } else {
            rows.stream().filter(row -> row.y >= CONTENT_TOP
                            && row.y + font.lineHeight <= contentBottom())
                    .forEach(row -> font.draw(stack, row.title,
                            panelX + 12, row.y, getEffectiveTheme().buttonText()));
        }
        renderWidgets(stack, partialTicks);
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs eventArgs) {
        if (eventArgs.mouseY() >= CONTENT_TOP && eventArgs.mouseY() < contentBottom()) {
            int next = Math.max(0, Math.min(maxContentOffset(),
                    contentOffset - (int) Math.signum(eventArgs.delta()) * ROW_STEP));
            if (next != contentOffset) {
                contentOffset = next;
                refreshWidget();
            }
            eventArgs.consumed(true);
        }
    }

    private ButtonWidget button(int x, int y, int width, int height, String text,
                                java.util.function.Consumer<ButtonWidget> action) {
        ButtonWidget button = new ButtonWidget(this);
        button.bounds(new ScreenCoordinate(x, y, Math.max(1, width), height));
        button.text(SakuraComponent.get().literal(text));
        button.onClick(action);
        addWidget(button);
        return button;
    }

    private ButtonWidget contentButton(int x, int y, int width, int height, String text,
                                       java.util.function.Consumer<ButtonWidget> action) {
        ButtonWidget button = button(x, y, width, height, text, action);
        button.visible(y >= CONTENT_TOP && y + height <= contentBottom());
        return button;
    }

    private int contentBottom() {
        return height - CONTENT_BOTTOM_MARGIN;
    }

    private int maxContentOffset() {
        return Math.max(0, contentHeight - Math.max(1, contentBottom() - CONTENT_TOP));
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
        if (ids.isEmpty()) {
            ids = preset.getCalendarIds();
        }
        int current = ids.indexOf(slot.getCalendarId());
        slot.setCalendarId(ids.get((current + 1 + ids.size()) % ids.size()));
        refreshWidget();
    }

    private void openDateInput(PersonalDatePreset preset, PlayerPersonalDateSlot slot) {
        boolean monthly = preset.getRecurrence() == PersonalDateRecurrence.MONTHLY;
        String current = monthly ? String.valueOf(slot.getDay())
                : slot.getMonth() + "/" + slot.getDay();
        Minecraft.getInstance().setScreen(new StringInputScreen(this,
                Text.trans(SakuraSignIn.MODID, monthly
                        ? "word.sakura_sign_in.personal_date_day"
                        : "word.sakura_sign_in.personal_date_month_day"),
                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.personal_date_value_hint"),
                monthly ? "[0-9]{1,3}" : "[0-9]{1,2}/[0-9]{1,3}", current,
                values -> {
                    String value = values.get(0);
                    String[] parts = value.split("/");
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

    private void save() {
        List<PlayerPersonalDateSlot> selected = slots.stream()
                .filter(slot -> slot.getDay() > 0)
                .collect(Collectors.toList());
        SakuraNetwork.sendToServer(new PersonalDateSlotUpdatePacket(selected));
        SakuraClientNotifications.success(SakuraComponent.get().transClient(
                "word", "personal_date_save_success"), SakuraNotificationTypes.REWARD);
    }

    private void openBaniraPlayerConfig() {
        Minecraft.getInstance().setScreen(new CustomPlayerConfigEditScreen(
                new CustomPlayerConfigEditScreen.Args()
                        .parentScreen(this)
                        .season(BaniraThemes.seasonFor(SakuraSignIn.MODID))));
    }

    private String calendarName(String id) {
        String key = SakuraClientState.getCalendarNames().get(id);
        if (key == null) {
            return id;
        }
        String translated = I18n.get(key);
        if (!translated.equals(key)) {
            return translated;
        }
        return key.indexOf('.') < 0 ? key : id;
    }

    private static final class Row {
        private final int y;
        private final String title;
        private final List<ButtonWidget> buttons = new ArrayList<>();

        private Row(int y, String title) {
            this.y = y;
            this.title = title;
        }
    }
}
