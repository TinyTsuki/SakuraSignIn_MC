package xin.vanilla.sakura.network.data;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.util.Component;

/**
 * 进度信息
 */
@Data
@Accessors(chain = true)
public class AdvancementData {
    @NonNull
    private final ResourceLocation id;
    @NonNull
    private final DisplayInfo displayInfo;

    public AdvancementData(@NonNull ResourceLocation id, DisplayInfo displayInfo) {
        this.id = id;
        if (displayInfo == null) {
            this.displayInfo = emptyDisplayInfo();
        } else {
            this.displayInfo = displayInfo;
        }
    }

    public static AdvancementData fromAdvancement(Advancement advancement) {
        DisplayInfo displayInfo = advancement.getDisplay();
        if (displayInfo == null) {
            return new AdvancementData(advancement.getId(), createDisplayInfo(advancement.getId().toString()));
        }
        return new AdvancementData(advancement.getId(), displayInfo);
    }

    public static AdvancementData readFromBuffer(BaniraPacketBuffer buffer) {
        BaniraIdentifier identifier = buffer.readIdentifier();
        ResourceLocation id = new ResourceLocation(identifier.getNamespace(), identifier.getPath());
        try {
            ItemStack icon = ItemStack.of(JsonToNBT.parseTag(buffer.readUtf()));
            ITextComponent title = ITextComponent.Serializer.fromJson(buffer.readUtf());
            ITextComponent description = ITextComponent.Serializer.fromJson(buffer.readUtf());
            String background = buffer.readUtf();
            FrameType frame = buffer.readEnum(FrameType.class);
            return new AdvancementData(id, new DisplayInfo(
                    icon,
                    title == null ? Component.literal("").toTextComponent() : title,
                    description == null ? Component.literal("").toTextComponent() : description,
                    background.isEmpty() ? null : new ResourceLocation(background),
                    frame,
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid advancement payload", exception);
        }
    }

    public static DisplayInfo emptyDisplayInfo() {
        return createDisplayInfo("");
    }

    public static DisplayInfo createDisplayInfo(String title) {
        return createDisplayInfo(title, "", new ItemStack(Items.GRASS_BLOCK, 99));
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        return createDisplayInfo(title, description, new ItemStack(Items.GRASS_BLOCK, 99));
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack
                , Component.literal(title).toTextComponent(), Component.literal(description).toTextComponent()
                , Optional.of(ResourceLocation.parse("")), AdvancementType.TASK
                , false, false, false);
    }

    public void writeToBuffer(BaniraPacketBuffer buffer) {
        buffer.writeIdentifier(BaniraIdentifier.of(id.getNamespace(), id.getPath()));
        buffer.writeUtf(displayInfo.getIcon().save(new CompoundNBT()).toString());
        buffer.writeUtf(ITextComponent.Serializer.toJson(displayInfo.getTitle()));
        buffer.writeUtf(ITextComponent.Serializer.toJson(displayInfo.getDescription()));
        buffer.writeUtf(displayInfo.getBackground() == null ? "" : displayInfo.getBackground().toString());
        buffer.writeEnum(displayInfo.getFrame());
        buffer.writeBoolean(displayInfo.shouldShowToast());
        buffer.writeBoolean(displayInfo.shouldAnnounceChat());
        buffer.writeBoolean(displayInfo.isHidden());
    }
}
