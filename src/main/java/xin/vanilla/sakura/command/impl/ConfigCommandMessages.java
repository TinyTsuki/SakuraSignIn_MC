package xin.vanilla.sakura.command.impl;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.Translator;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.text.SakuraComponent;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * 配置指令共享的保存与反馈逻辑。
 */
final class ConfigCommandMessages {
    private ConfigCommandMessages() {
    }

    static Component translated(ServerPlayerEntity player, String key, Object... args) {
        return SakuraComponent.get().trans(player, "message", key, args);
    }

    static Component enabled(ServerPlayerEntity player, boolean value) {
        return Translator.of(SakuraSignIn.MODID)
                .enabled(SakuraUtils.getPlayerLanguage(player), value);
    }

    static int saveAndBroadcast(ServerPlayerEntity player, String messageKey, Object... args) {
        CommonConfig.save();
        SakuraMessages.broadcast(player, translated(player, messageKey, args));
        return 1;
    }
}
