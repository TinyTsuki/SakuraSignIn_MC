package xin.vanilla.sakura.enums;

public enum EnumOperationType {
    ADD,
    SET,
    REMOVE,
    DEL,
    LIST,
    GET,
    CLEAR;

    public static EnumOperationType fromString(String type) {
        try {
            return EnumOperationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid operation type: " + type);
        }
    }
}
