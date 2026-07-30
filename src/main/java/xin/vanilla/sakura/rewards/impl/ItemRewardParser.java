package xin.vanilla.sakura.rewards.impl;

import xin.vanilla.sakura.text.SakuraComponent;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import lombok.NonNull;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import xin.vanilla.banira.common.util.ItemUtils;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.banira.common.data.Component;

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
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
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
            Item item = ItemUtils.getItemFromRegistry(itemId);
            if (item == null) {
                throw new JsonParseException("Unknown item ID: " + itemId);
            }
            itemStack = new ItemStack(item, count);

            // 如果存在NBT数据，则解析
            if (json.has("nbt")) {
                try {
                    CompoundNBT nbt = JsonToNBT.parseTag(json.get("nbt").getAsString());
                    itemStack.setTag(nbt);
                } catch (Exception e) {
                    LOGGER.debug("Failed to parse NBT data", e);
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
        // 原版悬浮名称组件可能被 ItemStack 缓存，不能在其上直接追加数量。
        return SakuraComponent.get().literal(itemStack.getHoverName().getString())
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
        return ItemUtils.getItemRegistryString(item);
    }

    public static String getId(ItemStack itemStack) {
        return getId(itemStack.getItem()) + getNbtString(itemStack);
    }

    public static Item getItem(String id) {
        String resourceId = id;
        if (id.contains("{") && id.endsWith("}")) resourceId = resourceId.substring(0, id.indexOf("{"));
        return ItemUtils.getItemFromRegistry(resourceId);
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
                CompoundNBT nbt = JsonToNBT.parseTag(nbtString);
                itemStack.setTag(nbt);
            } catch (Exception e) {
                if (throwException) throw e;
                LOGGER.error("Failed to parse NBT data", e);
            }
        }
        return itemStack;
    }
}
