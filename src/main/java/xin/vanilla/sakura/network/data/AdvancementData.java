package xin.vanilla.sakura.network.data;

import xin.vanilla.sakura.SakuraComponent;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

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

    public static AdvancementData fromAdvancement(AdvancementHolder advancement) {
        DisplayInfo displayInfo = advancement.value().display().orElse(null);
        return new AdvancementData(advancement.id(), displayInfo == null
                ? createDisplayInfo(advancement.id().toString()) : displayInfo);
    }

    public static AdvancementData readFromBuffer(BaniraPacketBuffer buffer) {
        BaniraIdentifier identifier = buffer.readIdentifier();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                identifier.getNamespace(), identifier.getPath());
        ResourceLocation itemId = ResourceLocation.parse(buffer.readUtf());
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(itemId), buffer.readVarInt());
        Component title = Component.literal(buffer.readUtf());
        Component description = Component.literal(buffer.readUtf());
        String background = buffer.readUtf();
        AdvancementType type = buffer.readEnum(AdvancementType.class);
        return new AdvancementData(id, new DisplayInfo(
                icon, title, description,
                background.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(ResourceLocation.parse(background)),
                type,
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        ));
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
                , SakuraComponent.get().literal(title).toVanilla(), SakuraComponent.get().literal(description).toVanilla()
                , java.util.Optional.empty(), AdvancementType.TASK
                , false, false, false);
    }

    public void writeToBuffer(BaniraPacketBuffer buffer) {
        buffer.writeIdentifier(BaniraIdentifier.of(id.getNamespace(), id.getPath()));
        buffer.writeUtf(BuiltInRegistries.ITEM.getKey(displayInfo.getIcon().getItem()).toString());
        buffer.writeVarInt(displayInfo.getIcon().getCount());
        buffer.writeUtf(displayInfo.getTitle().getString());
        buffer.writeUtf(displayInfo.getDescription().getString());
        buffer.writeUtf(displayInfo.getBackground().map(ResourceLocation::toString).orElse(""));
        buffer.writeEnum(displayInfo.getType());
        buffer.writeBoolean(displayInfo.shouldShowToast());
        buffer.writeBoolean(displayInfo.shouldAnnounceChat());
        buffer.writeBoolean(displayInfo.isHidden());
    }
}
