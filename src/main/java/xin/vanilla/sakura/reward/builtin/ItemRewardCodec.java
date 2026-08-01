package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
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
            Item item = location == null ? null : Registry.ITEM.getOptional(location).orElse(null);
            if (item == null || Registry.ITEM.getKey(item) == null) {
                throw new RewardDataException("Unknown item: " + itemId);
            }
            int count = content.get("count").getAsInt();
            if (count <= 0) {
                throw new RewardDataException("Item count must be positive");
            }
            ItemStack result = new ItemStack(item, count);
            if (content.has("nbt")) {
                CompoundNBT tag = JsonToNBT.parseTag(content.get("nbt").getAsString());
                result.setTag(tag);
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
        ResourceLocation itemId = value == null ? null : Registry.ITEM.getKey(value.getItem());
        if (value == null || value.isEmpty() || itemId == null
                || value.getCount() <= 0) {
            throw new RewardDataException("Item reward must contain a registered non-empty stack");
        }
        JsonObject result = new JsonObject();
        result.addProperty("item", itemId.toString());
        result.addProperty("count", value.getCount());
        if (value.hasTag() && value.getTag() != null) {
            result.addProperty("nbt", value.getTag().toString());
        }
        return result;
    }
}
