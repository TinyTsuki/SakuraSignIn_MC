package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.banira.common.util.StringUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresetValidator;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 个性化日期预设表单只让标识和名称自由输入，其余字段使用可解释选项。 */
public final class PersonalDatePresetForm {
    private PersonalDatePresetForm() {
    }

    public static Screen create(Screen parent, PersonalDatePreset existing,
                                Consumer<PersonalDatePreset> submit) {
        InputFormScreen.Args args = new InputFormScreen.Args()
                .setParentScreen(parent)
                .setHeaderTitle(text("personal_date_preset_title"));
        args.addWidget(textWidget("id", "personal_date_preset_id",
                existing == null ? "" : existing.getId(), "[a-z0-9_.-]{1,64}", value -> {
                    if (existing != null && !existing.getId().equals(value)) {
                        return tr("personal_date_preset_id_locked");
                    }
                    boolean duplicate = RewardConfigManager.getRewardConfig()
                            .getPersonalDatePresets().stream()
                            .anyMatch(candidate -> candidate != existing
                                    && value.equals(candidate.getId()));
                    return duplicate ? tr("personal_date_preset_duplicate") : "";
                }));
        args.addWidget(textWidget("name", "personal_date_preset_name",
                existing == null ? "" : existing.getDisplayName(), ".{1,64}", value -> ""));
        args.addWidget(dropdown("recurrence", "personal_date_recurrence",
                existing == null ? PersonalDateRecurrence.YEARLY.name()
                        : existing.getRecurrence().name(), false,
                option(PersonalDateRecurrence.YEARLY.name(), "personal_date_recurrence_yearly"),
                option(PersonalDateRecurrence.MONTHLY.name(), "personal_date_recurrence_monthly")));

        List<DropdownOption> calendars = SakuraClientState.getCalendarNames().entrySet().stream()
                .map(entry -> new DropdownOption(entry.getKey(), calendarName(entry.getKey(), entry.getValue()),
                        net.minecraft.world.item.ItemStack.EMPTY, null,
                        SakuraComponent.get().transClient("format", "personal_date_calendar_tooltip",
                                entry.getKey())))
                .collect(Collectors.toList());
        InputFormScreen.Widget calendarWidget = dropdown("calendars", "personal_date_calendars",
                existing == null ? "minecraft:gregorian"
                        : String.join(", ", existing.getCalendarIds()), true,
                calendars.toArray(new DropdownOption[0]));
        calendarWidget.validator(results -> StringUtils.isNullOrEmptyEx(results.value())
                ? tr("personal_date_calendar_required") : "");
        args.addWidget(calendarWidget);

        args.addWidget(numberWidget("slots", "personal_date_slots",
                existing == null ? 1 : existing.getMaxDateSlots(), 1, 16));
        args.addWidget(dropdown("delivery", "personal_date_delivery",
                existing == null ? PersonalDateDeliveryMode.SIGN_IN.name()
                        : existing.getDeliveryMode().name(), false,
                option(PersonalDateDeliveryMode.SIGN_IN.name(), "personal_date_delivery_sign_in"),
                option(PersonalDateDeliveryMode.ONLINE.name(), "personal_date_delivery_online")));
        args.addWidget(numberWidget("before", "personal_date_before",
                existing == null ? 0 : existing.getValidBeforeDays(), 0, 30));
        args.addWidget(numberWidget("after", "personal_date_after",
                existing == null ? 0 : existing.getValidAfterDays(), 0, 30));
        args.setCallback(results -> {
            List<String> calendarIds = Arrays.stream(results.value("calendars").split(","))
                    .map(value -> value.trim()).filter(StringUtils::isNotNullOrEmpty)
                    .distinct().collect(Collectors.toList());
            PersonalDatePreset candidate = new PersonalDatePreset(
                    results.value("id"), results.value("name"),
                    PersonalDateRecurrence.valueOf(results.value("recurrence")), calendarIds,
                    Integer.parseInt(results.value("slots")),
                    PersonalDateDeliveryMode.valueOf(results.value("delivery")),
                    Integer.parseInt(results.value("before")),
                    Integer.parseInt(results.value("after")),
                    existing == null ? new RewardList() : existing.getRewards());
            if (PersonalDatePresetValidator.validate(candidate).isEmpty()) {
                submit.accept(candidate);
            }
        });
        return new InputFormScreen(args);
    }

    private static InputFormScreen.Widget textWidget(
            String name, String titleKey, String value, String regex,
            java.util.function.Function<String, String> validator) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .hint(text(titleKey + "_hint")).tooltip(text(titleKey + "_hint"))
                .maxLength(regex.contains("64") ? 64 : 256)
                .regex(regex).defaultValue(value)
                .validator(results -> validator.apply(results.value()));
    }

    private static InputFormScreen.Widget numberWidget(String name, String titleKey,
                                                        int value, int min, int max) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .tooltip(text(titleKey + "_tooltip"))
                .maxLength(String.valueOf(max).length()).regex("[0-9]+")
                .defaultValue(String.valueOf(value)).validator(results -> {
                    try {
                        int parsed = Integer.parseInt(results.value());
                        return parsed >= min && parsed <= max ? "" : tr("personal_date_number_range");
                    } catch (NumberFormatException ignored) {
                        return tr("personal_date_number_range");
                    }
                });
    }

    private static InputFormScreen.Widget dropdown(
            String name, String titleKey, String value, boolean multi, DropdownOption... options) {
        return new InputFormScreen.Widget().name(name).title(text(titleKey))
                .type(InputFormScreen.WidgetType.DROPDOWN)
                .dropdownOptionEntries(Arrays.asList(options))
                .dropdownMultiSelect(multi).defaultValue(value);
    }

    private static DropdownOption option(String value, String key) {
        String label = tr(key);
        return new DropdownOption(value, label, net.minecraft.world.item.ItemStack.EMPTY,
                null, SakuraComponent.get().transClient("word", key + "_tooltip"));
    }

    private static String calendarName(String id, String displayNameKey) {
        if (displayNameKey == null || displayNameKey.isEmpty()) {
            return id;
        }
        String translated = net.minecraft.client.resources.language.I18n.get(displayNameKey);
        return displayNameKey.equals(translated) ? id : translated;
    }

    private static Text text(String key) {
        return Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in." + key);
    }

    private static String tr(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }
}
