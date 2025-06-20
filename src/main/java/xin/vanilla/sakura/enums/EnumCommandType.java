package xin.vanilla.sakura.enums;

import lombok.Getter;

@Getter
public enum EnumCommandType {
    HELP(false, false),
    LANGUAGE(false, false),
    LANGUAGE_CONCISE(),
    VIRTUAL_OP(),
    VIRTUAL_OP_CONCISE(),
    SIGN(),
    SIGN_CONCISE(),
    SIGNEX(),
    SIGNEX_CONCISE(),
    REWARD(),
    REWARD_CONCISE(),
    CDK(),
    CDK_CONCISE(),
    CARD(),
    CARD_CONCISE(),
    CARD_GET(true),
    CARD_GET_CONCISE(),
    CARD_SET(true),
    CARD_SET_CONCISE(),
    ;


    /**
     * 是否在帮助信息中忽略
     */
    private final boolean ignore;
    /**
     * 是否简短指令
     */
    private final boolean concise = this.name().endsWith("_CONCISE");
    /**
     * 是否被虚拟权限管理
     */
    private final boolean op;

    EnumCommandType() {
        this.ignore = false;
        this.op = !this.concise;
    }

    EnumCommandType(boolean ig) {
        this.ignore = ig;
        this.op = !this.concise;
    }

    EnumCommandType(boolean ig, boolean op) {
        this.ignore = ig;
        this.op = !this.concise && op;
    }

    public int getSort() {
        return this.ordinal();
    }

    public EnumCommandType replaceConcise() {
        if (this.name().endsWith("_CONCISE")) {
            return EnumCommandType.valueOf(this.name().replace("_CONCISE", ""));
        }
        return this;
    }
}
