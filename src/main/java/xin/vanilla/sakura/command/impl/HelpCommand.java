package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.SakuraComponent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraCommonSettings;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.banira.common.data.Component;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * 构建多页帮助指令，分页与标题格式复用 Banira 通用配置。
 */
public final class HelpCommand {
    private static final List<KeyValue<String, String>> ENTRIES = Arrays.asList(
            new KeyValue<>("/sakura help[ <page>]", "help"),
            new KeyValue<>("/sign[ <year> <month> <day>]", "concise_sign"),
            new KeyValue<>("/reward[ <year> <month> <day>]", "concise_reward"),
            new KeyValue<>("/signex[ <year> <month> <day>]", "concise_sign_and_reward"),
            new KeyValue<>("/cdk <key>", "concise_cdk"),
            new KeyValue<>("/sakura sign <year> <month> <day>", "sign"),
            new KeyValue<>("/sakura reward[ <year> <month> <day>]", "reward"),
            new KeyValue<>("/sakura signex[ <year> <month> <day>]", "sign_and_reward"),
            new KeyValue<>("/sakura cdk <key>", "cdk"),
            new KeyValue<>("/sakura lottery list", "lottery_list"),
            new KeyValue<>("/sakura lottery draw <pool> [count|all]", "lottery_draw"),
            new KeyValue<>("/sakura card give <num>[ <player>]", "card_give"),
            new KeyValue<>("/sakura card set <num>[ <player>]", "card_set"),
            new KeyValue<>("/sakura card get <player>", "card_get"),
            new KeyValue<>("/sakura config common <configKey> <configValue>", "config_common"),
            new KeyValue<>("/sakura config player personalDate", "config_personal_date")
    );

    private HelpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        Command<CommandSource> execute = context -> {
            int page = 1;
            try {
                page = IntegerArgumentType.getInteger(context, "page");
            } catch (IllegalArgumentException ignored) {
            }
            sendPage(context.getSource().getPlayerOrException(), page);
            return 1;
        };
        int pages = pageCount();
        return Commands.literal("help")
                .executes(execute)
                .then(Commands.argument("page", IntegerArgumentType.integer(1, pages))
                        .suggests((context, builder) -> {
                            for (int page = 1; page <= pages; page++) {
                                builder.suggest(page);
                            }
                            return builder.buildFuture();
                        })
                        .executes(execute));
    }

    public static int executeRoot(com.mojang.brigadier.context.CommandContext<CommandSource> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        sendPage(context.getSource().getPlayerOrException(), 1);
        return 1;
    }

    private static void sendPage(ServerPlayerEntity player, int page) {
        int perPage = BaniraCommonSettings.helpInfoNumPerPage();
        int pages = pageCount();
        Component help = SakuraComponent.get().literal(
                BaniraCommonSettings.formatHelpHeader("Sakura Sign In", page, pages) + "\n"
        );
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, ENTRIES.size());
        for (int index = start; index < end; index++) {
            KeyValue<String, String> entry = ENTRIES.get(index);
            Component description = SakuraComponent.get()
                    .trans(player, "word", entry.value())
                    .color(Color.GRAY.getRGB());
            help.append(entry.key())
                    .append(SakuraComponent.get().literal(" -> ").color(Color.YELLOW.getRGB()))
                    .append(description);
            if (index + 1 < end) {
                help.append("\n");
            }
        }
        SakuraMessages.send(player, help, SakuraNotificationTypes.HELP);
    }

    private static int pageCount() {
        return Math.max(1, (int) Math.ceil(
                (double) ENTRIES.size() / BaniraCommonSettings.helpInfoNumPerPage()
        ));
    }
}
