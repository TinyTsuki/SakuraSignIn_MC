package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class PersonalDateInputForm {
    private PersonalDateInputForm() {
    }

    public static Screen create(Screen parent, PersonalDatePreset preset, PlayerPersonalDateSlot slot,
                                BooleanSupplier acceptsResult, Runnable refresh) {
        boolean monthly = preset.getRecurrence() == PersonalDateRecurrence.MONTHLY;
        String regex = monthly ? "[0-9]{1,3}" : "[0-9]{1,2}/[0-9]{1,3}";
        String current = monthly ? String.valueOf(slot.getDay()) : slot.getMonth() + "/" + slot.getDay();
        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen(parent)
                .setTitle(Text.trans(SakuraSignIn.MODID, monthly
                        ? "word.sakura_sign_in.personal_date_day"
                        : "word.sakura_sign_in.personal_date_month_day"));
        args.addWidget(new InputFormScreen.Widget().name("date")
                .title(args.getTitle()).hint(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.personal_date_value_hint"))
                .tooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.personal_date_value_hint"))
                .regex(regex).defaultValue(current)
                .validator(values -> validFormat(values.value(), regex) ? "" : formatError()));
        AtomicBoolean submitted = new AtomicBoolean();
        args.setCallback(values -> {
            String value = values.value("date");
            if (!validFormat(value, regex)) throw new IllegalArgumentException(formatError());
            int month;
            int day;
            if (monthly) {
                month = 0;
                day = Integer.parseInt(value);
            } else {
                String[] parts = value.split("/", -1);
                month = Integer.parseInt(parts[0]);
                day = Integer.parseInt(parts[1]);
            }
            if (!acceptsResult.getAsBoolean() || !submitted.compareAndSet(false, true)) return;
            slot.setMonth(month);
            slot.setDay(day);
            refresh.run();
        });
        return new InputFormScreen(args);
    }

    private static boolean validFormat(String value, String regex) {
        return value != null && value.matches(regex);
    }

    private static String formatError() {
        return SakuraComponent.get().translateClient("word", "input_format_invalid");
    }
}
