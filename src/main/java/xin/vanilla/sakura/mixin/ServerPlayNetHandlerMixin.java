package xin.vanilla.sakura.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.client.CClientSettingsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * 监听玩家客户端设置信息同步包并获取玩家语言</br>
 * 自动签到依赖于此事件产生的playerLanguageCache</br>
 * 若无此事件请另寻他法
 */
@Mixin(ServerPlayNetHandler.class)
public class ServerPlayNetHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            at = {@At("TAIL")},
            method = {"handleClientInformation"}
    )
    public void clientInformation(CClientSettingsPacket packet, CallbackInfo callbackInfo) {
        SakuraSignIn.LOGGER.debug("Player language: {}.", SakuraUtils.getPlayerLanguage(player));
        SakuraSignIn.getPlayerLanguageCache().put(SakuraUtils.getPlayerUUIDString(this.player), packet.getLanguage());
    }
}
