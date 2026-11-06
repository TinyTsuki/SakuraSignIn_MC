package xin.vanilla.sakura.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class SakuraNbt {
    private SakuraNbt() {
    }

    public static ListTag compoundListOrEmpty(CompoundTag tag, String key) {
        return tag.getList(key).filter(SakuraNbt::isCompoundList).orElseGet(ListTag::new);
    }

    public static boolean isCompoundList(ListTag list) {
        return list.stream().allMatch(CompoundTag.class::isInstance);
    }
}
