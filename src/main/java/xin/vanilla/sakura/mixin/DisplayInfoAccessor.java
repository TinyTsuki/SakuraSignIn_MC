package xin.vanilla.sakura.mixin;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep advancement serialization on common fields across Fabric versions. */
@Mixin(DisplayInfo.class)
public interface DisplayInfoAccessor {
    @Accessor("icon")
    ItemStack sakura$getIcon();

    @Accessor("background")
    ResourceLocation sakura$getBackground();

    @Accessor("showToast")
    boolean sakura$shouldShowToast();
}
