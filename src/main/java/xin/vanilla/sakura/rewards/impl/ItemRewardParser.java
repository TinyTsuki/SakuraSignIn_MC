package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import lombok.NonNull;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

import static xin.vanilla.sakura.config.RewardConfigManager.GSON;

public class ItemRewardParser implements RewardParser<ItemStack> {

    @NonNull
    private static ItemStack getItemStack(JsonObject jsonObject) {
        if (jsonObject.has("item")) {
            jsonObject.add("id", jsonObject.remove("item"));
        }
        if (jsonObject.has("nbt")) {
            jsonObject.add("components", jsonObject.remove("nbt"));
        }
        ItemStack first = ItemStack.CODEC.decode(JsonOps.INSTANCE, jsonObject).result().orElse(new Pair<>(null, null)).getFirst();
        if (first == null && jsonObject.has("id")) {
            try {
                String itemId = jsonObject.get("id").getAsString();
                int count = 1;
                if (jsonObject.has("count")) {
                    count = Math.max(jsonObject.get("count").getAsInt(), 1);
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item == null) {
                    item = Items.AIR;
                }
                first = new ItemStack(item, count);
            } catch (Exception e) {
                LOGGER.debug("Failed to parse item reward", e);
            }
        }
        return first == null ? new ItemStack(Items.AIR) : first;
    }

    private static JsonObject getJsonObject(ItemStack reward) {
        JsonObject jsonObject = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, reward).result().orElse(new JsonObject()).getAsJsonObject();
        if (jsonObject.has("id")) {
            jsonObject.add("item", jsonObject.remove("id"));
        }
        if (jsonObject.has("components")) {
            jsonObject.add("nbt", jsonObject.remove("components"));
        }
        return jsonObject;
    }

    @Override
    public @NonNull ItemStack deserialize(JsonObject json) {
        JsonObject jsonObject = json.deepCopy();
        return getItemStack(jsonObject);
    }

    public static ItemStack deserializeFromString(String json) {
        JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);
        return getItemStack(jsonObject);
    }

    @Override
    public JsonObject serialize(ItemStack reward) {
        return getJsonObject(reward);
    }

    public static String serializeToString(ItemStack reward) {
        return getJsonObject(reward).toString();
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        ItemStack itemStack = this.deserialize(json);
        return Component.original(itemStack.getHoverName())
                .append(withNum ? "x" + itemStack.getCount() : "");
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
        JsonObject json = new JsonObject();
        try {
            if (!itemStack.getComponents().isEmpty()) {
                DataResult<JsonElement> jsonElementDataResult = DataComponentMap.CODEC.encodeStart(JsonOps.INSTANCE, itemStack.getComponents());
                if (jsonElementDataResult != null) {
                    json = jsonElementDataResult
                            .result()
                            .orElse(new JsonObject())
                            .getAsJsonObject();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse NBT data", e);
        }
        return json.toString();
    }

    public static String getId(Item item) {
        ResourceLocation resource = BuiltInRegistries.ITEM.getKey(item);
        if (resource == null) return "minecraft:air";
        else return resource.toString();
    }

    public static String getId(ItemStack itemStack) {
        return getId(itemStack.getItem()) + getNbtString(itemStack);
    }

    public static Item getItem(String id) {
        String resourceId = id;
        if (id.contains("{") && id.endsWith("}")) resourceId = resourceId.substring(0, id.indexOf("{"));
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(resourceId));
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
                DataComponentMap components = DataComponentMap.CODEC.decode(JsonOps.INSTANCE, GSON.fromJson(nbtString, JsonObject.class))
                        .result().orElse(new Pair<>(DataComponentMap.EMPTY, null)).getFirst();
                itemStack.applyComponents(components);
            } catch (Exception e) {
                if (throwException) throw e;
                LOGGER.error("Failed to parse NBT data", e);
            }
        }
        return itemStack;
    }
}
