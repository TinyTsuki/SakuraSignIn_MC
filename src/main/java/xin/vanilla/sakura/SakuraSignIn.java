package xin.vanilla.sakura;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import xin.vanilla.sakura.client.SakuraClientBootstrap;
import xin.vanilla.sakura.internal.forge.ForgeSakuraEntrypoint;

/**
 * Forge 模组入口，只负责选择公共、加载器与客户端启动器。
 */
@Mod(SakuraSignIn.MODID)
public final class SakuraSignIn {
    public static final String DEFAULT_COMMAND_PREFIX = "sakura";
    public static final String MODID = "sakura_sign_in";

    public SakuraSignIn() {
        SakuraCommonBootstrap.init();
        ForgeSakuraEntrypoint.init();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> SakuraClientBootstrap::init);
    }
}
