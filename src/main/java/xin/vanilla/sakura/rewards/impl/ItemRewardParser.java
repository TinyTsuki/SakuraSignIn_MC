package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import xin.vanilla.sakura.rewards.RewardParser;

public class ItemRewardParser implements RewardParser<ItemStack> {

    @Override
    public @NonNull ItemStack deserialize(JsonObject json) {
        ItemStack itemStack;
        try {
            String itemId;
            if (json.has("id")) {
                itemId = json.get("id").getAsString();
            } else {
                itemId = json.get("item").getAsString();
            }
            int count = json.get("count").getAsInt();
            count = Math.max(count, 1);
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) {
                throw new JsonParseException("Unknown item ID: " + itemId);
            }
            itemStack = new ItemStack(item, count);

            // 如果存在NBT数据，则解析
            if (json.has("nbt")) {
                try {
                    CompoundTag nbt = TagParser.parseTag(json.get("nbt").getAsString());
                    itemStack.setTag(nbt);
                } catch (CommandSyntaxException e) {
                    LOGGER.warn("Failed to parse NBT data");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize item reward", e);
            itemStack = new ItemStack(Items.AIR);
        }
        return itemStack;
    }

    @Override
    public JsonObject serialize(ItemStack reward) {
        JsonObject json = new JsonObject();
        try {
            json.addProperty("item", getId(reward.getItem()));
            json.addProperty("count", reward.getCount());

            // 如果物品有NBT数据，则序列化
            if (reward.hasTag()) {
                if (reward.getTag() != null) {
                    json.addProperty("nbt", getNbtString(reward));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to serialize item reward", e);
            json.addProperty("item", getId(Items.AIR));
            json.addProperty("count", 0);
        }
        return json;
    }

    @Override
    public @NonNull String getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull String getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        ItemStack itemStack = this.deserialize(json);
        return itemStack.getDisplayName().getString().replaceAll("\\[(.*)]", "$1") + (withNum ? "x" + itemStack.getCount() : "");
    }

    public @NonNull
    static String getDisplayName(ItemStack itemStack) {
        return itemStack.getDisplayName().getString().replaceAll("\\[(.*)]", "$1");
    }

    public @NonNull
    static String getDisplayName(Item item) {
        return new ItemStack(item).getDisplayName().getString().replaceAll("\\[(.*)]", "$1");
    }

    public static String getNbtString(ItemStack itemStack) {
        String json = "";
        if (itemStack.hasTag() && itemStack.getTag() != null) {
            json = itemStack.getTag().toString();
        }
        return json;
    }

    public static String getId(Item item) {
        ResourceLocation resource = item.getRegistryName();
        if (resource == null) return "minecraft:air";
        else return resource.toString();
    }

    public static String getId(ItemStack itemStack) {
        return getId(itemStack.getItem()) + getNbtString(itemStack);
    }

    public static Item getItem(String id) {
        String resourceId = id;
        if (id.contains("{") && id.endsWith("}")) resourceId = resourceId.substring(0, id.indexOf("{"));
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(resourceId));
    }

    public static ItemStack getItemStack(String id) {
        ItemStack result = new ItemStack(Items.AIR);
        try {
            result = getItemStack(id, false);
        } catch (CommandSyntaxException ignored) {
        }
        return result;
    }

    public static ItemStack getItemStack(String id, boolean throwException) throws CommandSyntaxException {
        Item item = getItem(id);
        if (item == null) {
            throw new RuntimeException("Unknown item ID: " + id);
        }
        ItemStack itemStack = new ItemStack(item);
        if (id.contains("{") && id.endsWith("}") && !id.endsWith("{}")) {
            try {
                String nbtString = id.substring(id.indexOf("{"));
                CompoundTag nbt = TagParser.parseTag(nbtString);
                itemStack.setTag(nbt);
            } catch (Exception e) {
                if (throwException) throw e;
                LOGGER.error("Failed to parse NBT data", e);
            }
        }
        return itemStack;
    }
}
