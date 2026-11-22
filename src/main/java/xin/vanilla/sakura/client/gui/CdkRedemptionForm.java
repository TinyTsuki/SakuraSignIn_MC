package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.CommonConfig;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CdkRedemptionForm {
    private CdkRedemptionForm() {
    }

    public static Screen create(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        Object connection = client.getConnection();
        String prefix = CommonConfig.get().command().commandPrefix();
        String subcommand = CommonConfig.get().command().commandCdk();
        AtomicBoolean submitted = new AtomicBoolean();
        InputFormScreen.Args args = new InputFormScreen.Args().setParentScreen(parent)
                .setHeaderTitle(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.quick_action_cdk"));
        args.addWidget(new InputFormScreen.Widget().name("code").maxLength(256)
                .title(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.cdk_code"))
                .validator(values -> {
                    try {
                        command(prefix, subcommand, values.value());
                        return "";
                    } catch (IllegalArgumentException invalid) {
                        return message("cdk_input_invalid");
                    }
                }));
        args.setCallback(values -> {
            if (connection == null || client.getConnection() != connection || client.player == null) {
                values.runningResult(message("cdk_session_changed"));
                return;
            }
            final String command;
            try {
                command = command(prefix, subcommand, values.value("code"));
            } catch (IllegalArgumentException invalid) {
                values.runningResult(message("cdk_input_invalid"));
                return;
            }
            if (submitted.compareAndSet(false, true)) client.player.chat(command);
        });
        return new InputFormScreen(args);
    }

    static String command(String prefix, String subcommand, String code) {
        if (prefix == null || subcommand == null || code == null
                || prefix.trim().isEmpty() || subcommand.trim().isEmpty() || code.trim().isEmpty())
            throw new IllegalArgumentException("Missing redemption command or code");
        String command = "/" + prefix.trim() + " " + subcommand.trim() + " " + code.trim();
        // The 1.16.5 chat packet truncates at 256; never redeem a truncated code.
        if (command.length() > 256 || (prefix + subcommand + code).chars()
                .anyMatch(c -> c < 32 || c == 127 || c == 167))
            throw new IllegalArgumentException("Invalid redemption command");
        return command;
    }

    private static String message(String key) {
        return SakuraComponent.get().translateClient("word", key);
    }
}
