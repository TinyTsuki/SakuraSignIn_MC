package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class ItemRewardCodec implements RewardCodec<ItemStack> {
    @Override
    public ItemStack decode(JsonObject content) throws RewardDataException {
        try {
            String itemId = content.has("item")
                    ? content.get("item").getAsString()
                    : content.get("id").getAsString();
            ResourceLocation location = ResourceLocation.tryParse(itemId);
            Item item = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
            if (item == null || BuiltInRegistries.ITEM.getKey(item) == null) {
                throw new RewardDataException("Unknown item: " + itemId);
            }
            int count = content.get("count").getAsInt();
            if (count <= 0) {
                throw new RewardDataException("Item count must be positive");
            }
            ItemStack result = new ItemStack(item, count);
            if (content.has("components")) {
                DataComponentPatch patch = DataComponentPatch.CODEC
                        .parse(registryOps(), content.get("components"))
                        .getOrThrow();
                result.applyComponentsAndValidate(patch);
            }
            if (content.has("nbt")) {
                CompoundTag tag = TagParser.parseTag(content.get("nbt").getAsString());
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return result;
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid item reward", exception);
        }
    }

    @Override
    public JsonObject encode(ItemStack value) throws RewardDataException {
        ResourceLocation itemId = value == null ? null : BuiltInRegistries.ITEM.getKey(value.getItem());
        if (value == null || value.isEmpty() || itemId == null
                || value.getCount() <= 0) {
            throw new RewardDataException("Item reward must contain a registered non-empty stack");
        }
        JsonObject result = new JsonObject();
        result.addProperty("item", itemId.toString());
        result.addProperty("count", value.getCount());
        DataComponentPatch components = value.getComponentsPatch();
        if (!components.isEmpty()) {
            result.add("components", DataComponentPatch.CODEC
                    .encodeStart(registryOps(), components)
                    .getOrThrow());
        }
        return result;
    }

    private static RegistryOps<com.google.gson.JsonElement> registryOps() {
        return RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
