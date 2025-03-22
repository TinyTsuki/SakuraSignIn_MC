package xin.vanilla.sakura.network.data;

import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.sakura.util.Component;

import java.util.Objects;

/**
 * 进度信息
 */
@Accessors(chain = true)
public record AdvancementData(@NonNull ResourceLocation id, @NonNull DisplayInfo displayInfo) {
    public AdvancementData(@NonNull ResourceLocation id, DisplayInfo displayInfo) {
        this.id = id;
        this.displayInfo = Objects.requireNonNullElseGet(displayInfo, AdvancementData::emptyDisplayInfo);
    }

    public static AdvancementData fromAdvancement(Advancement advancement) {
        DisplayInfo displayInfo = advancement.getDisplay();
        return new AdvancementData(advancement.getId(), Objects.requireNonNullElseGet(displayInfo, () -> createDisplayInfo(advancement.getId().toString())));
    }

    public static AdvancementData readFromBuffer(FriendlyByteBuf buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        return new AdvancementData(id, DisplayInfo.fromNetwork(buffer));
    }

    public static DisplayInfo emptyDisplayInfo() {
        return createDisplayInfo("");
    }

    public static DisplayInfo createDisplayInfo(String title) {
        return createDisplayInfo(title, "", new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        return createDisplayInfo(title, description, new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack
                , Component.literal(title).toTextComponent(), Component.literal(description).toTextComponent()
                , new ResourceLocation(""), FrameType.TASK
                , false, false, false);
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(id);
        displayInfo.serializeToNetwork(buffer);
    }
}
