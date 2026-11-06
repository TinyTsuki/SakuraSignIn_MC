package xin.vanilla.sakura.test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class NativeBootstrap {
    private static boolean componentsBound;

    private NativeBootstrap() {
    }

    public static synchronized void initialize() {
        installStandaloneLoader();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (componentsBound) return;
        var lookup = VanillaRegistries.createLookup();
        // The vanilla lookup uses empty tag placeholders absent from a full NeoForge reload.
        boolean developmentValidation = SharedConstants.IS_RUNNING_IN_IDE;
        try {
            SharedConstants.IS_RUNNING_IN_IDE = false;
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                    .forEach(DataComponentInitializers.PendingComponents::apply);
        } finally {
            SharedConstants.IS_RUNNING_IN_IDE = developmentValidation;
        }
        componentsBound = true;
    }

    @SuppressWarnings("unchecked")
    private static void installStandaloneLoader() {
        if (FMLLoader.getCurrentOrNull() != null) return;
        try {
            // FeatureFlags needs a discovered-mod list even when no game is running.
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeType.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            FMLLoader loader = (FMLLoader) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), FMLLoader.class);
            var listField = FMLLoader.class.getDeclaredField("loadingModList");
            listField.setAccessible(true);
            listField.set(loader, LoadingModList.of(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
            var currentField = FMLLoader.class.getDeclaredField("current");
            currentField.setAccessible(true);
            ((AtomicReference<FMLLoader>) currentField.get(null)).compareAndSet(null, loader);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot initialize the headless NeoForge fixture", error);
        }
    }
}
