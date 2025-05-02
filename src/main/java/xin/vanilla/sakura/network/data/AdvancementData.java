package xin.vanilla.sakura.network.data;

import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.sakura.util.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * 进度信息
 */
@Accessors(chain = true)
public record AdvancementData(@NonNull ResourceLocation id, @NonNull DisplayInfo displayInfo) {
    public AdvancementData(@NonNull ResourceLocation id, DisplayInfo displayInfo) {
        this.id = id;
        this.displayInfo = Objects.requireNonNullElseGet(displayInfo, AdvancementData::emptyDisplayInfo);
    }

    public static AdvancementData fromAdvancement(AdvancementHolder advancement) {
        DisplayInfo displayInfo = advancement.value().display().orElse(null);
        return new AdvancementData(advancement.id(), Objects.requireNonNullElseGet(displayInfo, () -> createDisplayInfo(advancement.id().toString())));
    }

    public static AdvancementData readFromBuffer(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        return new AdvancementData(id, DisplayInfo.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buffer));
    }

    public static DisplayInfo emptyDisplayInfo() {
        return createDisplayInfo("");
    }

    public static DisplayInfo createDisplayInfo(String title) {
        ItemStack itemStack = new ItemStack(Items.LIGHT);
        itemStack.set(DataComponents.CUSTOM_NAME, Component.literal("empty").toTextComponent());
        return createDisplayInfo(title, "", itemStack);
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        ItemStack itemStack = new ItemStack(Items.LIGHT);
        itemStack.set(DataComponents.CUSTOM_NAME, Component.literal("empty").toTextComponent());
        return createDisplayInfo(title, description, itemStack);
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack
                , Component.literal(title).toTextComponent(), Component.literal(description).toTextComponent()
                , Optional.of(ResourceLocation.parse("")), AdvancementType.TASK
                , false, false, false);
    }

    public void writeToBuffer(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
        DisplayInfo.STREAM_CODEC.encode(buffer, displayInfo);
    }
}
