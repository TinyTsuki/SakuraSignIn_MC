package xin.vanilla.sakura.test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

public final class NativeBootstrap {
    private static boolean componentsBound;

    private NativeBootstrap() {
    }

    public static synchronized void initialize() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (componentsBound) return;
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
        componentsBound = true;
    }
}
