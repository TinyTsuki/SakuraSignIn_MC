package xin.vanilla.sakura.network.data;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 进度信息
 */
@Data
@Accessors(chain = true)
public class AdvancementData {
    private static final String NO_ICON_ID = "minecraft:air";

    private static final class EmptyIcon {
        private static final ItemStackTemplate TEMPLATE = new ItemStackTemplate(Items.STRUCTURE_VOID);
    }

    @NonNull
    private final Identifier id;
    @NonNull
    private final DisplayInfo displayInfo;

    public AdvancementData(@NonNull Identifier id, DisplayInfo displayInfo) {
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
        Identifier id = Identifier.fromNamespaceAndPath(
                identifier.getNamespace(), identifier.getPath());
        Identifier itemId = Identifier.parse(buffer.readUtf());
        int count = buffer.readVarInt();
        var item = BuiltInRegistries.ITEM.get(itemId);
        ItemStackTemplate icon = count > 0 && item.isPresent() && item.get().value() != Items.AIR
                ? new ItemStackTemplate(item.get(), count, net.minecraft.core.component.DataComponentPatch.EMPTY)
                : EmptyIcon.TEMPLATE;
        Component title = Component.literal(buffer.readUtf());
        Component description = Component.literal(buffer.readUtf());
        String background = buffer.readUtf();
        AdvancementType type = buffer.readEnum(AdvancementType.class);
        return new AdvancementData(id, new DisplayInfo(
                icon, title, description,
                background.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(new ClientAsset.ResourceTexture(Identifier.parse(background))),
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
        return createDisplayInfo(title, "", ItemStack.EMPTY);
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        return createDisplayInfo(title, description, ItemStack.EMPTY);
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack.isEmpty() ? EmptyIcon.TEMPLATE : ItemStackTemplate.fromNonEmptyStack(itemStack)
                , SakuraComponent.get().literal(title).toVanilla(), SakuraComponent.get().literal(description).toVanilla()
                , java.util.Optional.empty(), AdvancementType.TASK
                , false, false, false);
    }

    public ItemStack iconStack() {
        ItemStackTemplate icon = displayInfo.getIcon();
        return icon == EmptyIcon.TEMPLATE ? ItemStack.EMPTY : icon.create();
    }

    public void writeToBuffer(BaniraPacketBuffer buffer) {
        buffer.writeIdentifier(BaniraIdentifier.of(id.getNamespace(), id.getPath()));
        ItemStackTemplate icon = displayInfo.getIcon();
        boolean missing = icon == EmptyIcon.TEMPLATE;
        buffer.writeUtf(missing ? NO_ICON_ID : icon.typeHolder().unwrapKey().orElseThrow().identifier().toString());
        buffer.writeVarInt(missing ? 0 : icon.count());
        buffer.writeUtf(displayInfo.getTitle().getString());
        buffer.writeUtf(displayInfo.getDescription().getString());
        buffer.writeUtf(displayInfo.getBackground().map(asset -> asset.id().toString()).orElse(""));
        buffer.writeEnum(displayInfo.getType());
        buffer.writeBoolean(displayInfo.shouldShowToast());
        buffer.writeBoolean(displayInfo.shouldAnnounceChat());
        buffer.writeBoolean(displayInfo.isHidden());
    }
}
