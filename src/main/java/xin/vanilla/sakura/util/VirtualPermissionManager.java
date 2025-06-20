package xin.vanilla.sakura.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import xin.vanilla.sakura.config.CustomConfig;
import xin.vanilla.sakura.enums.EnumCommandType;
import xin.vanilla.sakura.enums.EnumOperationType;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class VirtualPermissionManager {

    private static final Map<String, Set<EnumCommandType>> OP_MAP = deserialize();

    /**
     * 添加权限（合并原有权限）
     */
    public static void addVirtualPermission(PlayerEntity player, EnumCommandType... types) {
        modifyPermissions(SakuraUtils.getPlayerUUIDString(player), EnumOperationType.ADD, types);
    }

    /**
     * 设置权限（覆盖原有权限）
     */
    public static void setVirtualPermission(PlayerEntity player, EnumCommandType... types) {
        modifyPermissions(SakuraUtils.getPlayerUUIDString(player), EnumOperationType.SET, types);
    }

    /**
     * 删除权限
     */
    public static void delVirtualPermission(PlayerEntity player, EnumCommandType... types) {
        modifyPermissions(SakuraUtils.getPlayerUUIDString(player), EnumOperationType.REMOVE, types);
    }

    /**
     * 清空所有权限
     */
    public static void clearVirtualPermission(PlayerEntity player) {
        modifyPermissions(SakuraUtils.getPlayerUUIDString(player), EnumOperationType.CLEAR);
    }

    /**
     * 获取当前权限列表
     */
    public static Set<EnumCommandType> getVirtualPermission(PlayerEntity player) {
        return getExistingPermissions(SakuraUtils.getPlayerUUIDString(player));
    }

    public static String buildPermissionsString(EnumCommandType... types) {
        return Arrays.stream(types)
                .filter(EnumCommandType::isOp)
                .sorted(Comparator.comparingInt(EnumCommandType::getSort))
                .map(EnumCommandType::name)
                .collect(Collectors.joining(","));
    }

    public static String buildPermissionsString(Set<EnumCommandType> types) {
        return types.stream()
                .filter(EnumCommandType::isOp)
                .sorted(Comparator.comparingInt(EnumCommandType::getSort))
                .map(EnumCommandType::name)
                .collect(Collectors.joining(","));
    }

    private static void modifyPermissions(String stringUUID, EnumOperationType operation, EnumCommandType... types) {
        Set<EnumCommandType> newTypes = processOperation(getExistingPermissions(stringUUID), new HashSet<>(Arrays.asList(types)), operation);
        updateRuleList(stringUUID, newTypes);
    }

    /**
     * 查找现有规则
     */
    private static Set<EnumCommandType> getExistingPermissions(String uuid) {
        return OP_MAP.getOrDefault(uuid, new HashSet<>());
    }

    /**
     * 处理权限操作
     */
    private static Set<EnumCommandType> processOperation(Set<EnumCommandType> existing, Set<EnumCommandType> input, EnumOperationType operation) {
        Set<EnumCommandType> result = new LinkedHashSet<>(existing);
        switch (operation) {
            case ADD:
                result.addAll(input);
                break;
            case SET:
                result.clear();
                result.addAll(input);
                break;
            case DEL:
            case REMOVE:
                input.forEach(result::remove);
                break;
            case CLEAR:
                result.clear();
                break;
        }
        return result.stream().filter(EnumCommandType::isOp).collect(Collectors.toSet());
    }

    /**
     * 更新规则列表
     */
    private static void updateRuleList(String stringUUID, Set<EnumCommandType> types) {
        OP_MAP.putAll(deserialize());
        OP_MAP.put(stringUUID, types);
        CustomConfig.setVirtualPermission(serialize());
    }

    private static JsonObject serialize() {
        JsonObject jsonObject = new JsonObject();
        OP_MAP.forEach((uuid, types) -> {
            JsonArray jsonArray = new JsonArray();
            types.stream().map(EnumCommandType::name).forEach(jsonArray::add);
            jsonObject.add(uuid, jsonArray);
        });
        return jsonObject;
    }

    private static Map<String, Set<EnumCommandType>> deserialize(JsonObject jsonObject) {
        Map<String, Set<EnumCommandType>> map = new HashMap<>();
        jsonObject.entrySet().forEach(entry -> {
            Set<EnumCommandType> types = new HashSet<>();
            entry.getValue().getAsJsonArray().forEach(jsonElement -> types.add(EnumCommandType.valueOf(jsonElement.getAsString())));
            map.put(entry.getKey(), types);
        });
        return map;
    }

    private static Map<String, Set<EnumCommandType>> deserialize() {
        Map<String, Set<EnumCommandType>> result;
        try {
            result = deserialize(CustomConfig.getVirtualPermission());
        } catch (Exception e) {
            result = new HashMap<>();
        }
        return result;
    }

}
