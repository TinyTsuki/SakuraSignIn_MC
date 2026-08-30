package xin.vanilla.sakura.network.data;

import xin.vanilla.sakura.SakuraComponent;
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
import xin.vanilla.banira.common.data.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度信息
 */
@Data
@Accessors(chain = true)
public class AdvancementData {
    private static final Map<Class<?>, Field[]> DISPLAY_FIELDS = new ConcurrentHashMap<>();
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

    public static ItemStack displayIcon(DisplayInfo displayInfo) {
        return ((ItemStack) displayValue(displayInfo, ItemStack.class, 0)).copy();
    }

    public static ITextComponent displayTitle(DisplayInfo displayInfo) {
        return (ITextComponent) displayValue(displayInfo, ITextComponent.class, 0);
    }

    public static ITextComponent displayDescription(DisplayInfo displayInfo) {
        return (ITextComponent) displayValue(displayInfo, ITextComponent.class, 1);
    }

    public static ResourceLocation displayBackground(DisplayInfo displayInfo) {
        return (ResourceLocation) displayValue(displayInfo, ResourceLocation.class, 0);
    }

    public static FrameType displayFrame(DisplayInfo displayInfo) {
        return (FrameType) displayValue(displayInfo, FrameType.class, 0);
    }

    public static boolean displayShowToast(DisplayInfo displayInfo) {
        return (Boolean) displayValue(displayInfo, boolean.class, 0);
    }

    public static boolean displayAnnounceChat(DisplayInfo displayInfo) {
        return (Boolean) displayValue(displayInfo, boolean.class, 1);
    }

    public static boolean displayHidden(DisplayInfo displayInfo) {
        return (Boolean) displayValue(displayInfo, boolean.class, 2);
    }

    private static Object displayValue(DisplayInfo displayInfo, Class<?> type, int occurrence) {
        Field[] fields = DISPLAY_FIELDS.computeIfAbsent(type, AdvancementData::displayFields);
        if (occurrence >= fields.length) {
            throw new IllegalStateException("DisplayInfo field was not found for " + type.getName() + '[' + occurrence + ']');
        }
        try {
            return fields[occurrence].get(displayInfo);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read advancement display data", exception);
        }
    }

    private static Field[] displayFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field candidate : DisplayInfo.class.getDeclaredFields()) {
            if (candidate.getType() == type) {
                candidate.setAccessible(true);
                fields.add(candidate);
            }
        }
        return fields.toArray(new Field[0]);
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
                    title == null ? SakuraComponent.get().literal("").toVanilla() : title,
                    description == null ? SakuraComponent.get().literal("").toVanilla() : description,
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
        return createDisplayInfo(title, "", new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        return createDisplayInfo(title, description, new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack
                , SakuraComponent.get().literal(title).toVanilla(), SakuraComponent.get().literal(description).toVanilla()
                , new ResourceLocation(""), FrameType.TASK
                , false, false, false);
    }

    public void writeToBuffer(BaniraPacketBuffer buffer) {
        buffer.writeIdentifier(BaniraIdentifier.of(id.getNamespace(), id.getPath()));
        buffer.writeUtf(displayIcon(displayInfo).save(new CompoundNBT()).toString());
        buffer.writeUtf(ITextComponent.Serializer.toJson(displayTitle(displayInfo)));
        buffer.writeUtf(ITextComponent.Serializer.toJson(displayDescription(displayInfo)));
        ResourceLocation background = displayBackground(displayInfo);
        buffer.writeUtf(background == null ? "" : background.toString());
        buffer.writeEnum(displayFrame(displayInfo));
        buffer.writeBoolean(displayShowToast(displayInfo));
        buffer.writeBoolean(displayAnnounceChat(displayInfo));
        buffer.writeBoolean(displayHidden(displayInfo));
    }
}
