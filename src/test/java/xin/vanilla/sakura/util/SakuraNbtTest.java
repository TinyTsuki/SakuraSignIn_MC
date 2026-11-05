package xin.vanilla.sakura.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import org.junit.Test;

import static org.junit.Assert.*;

public class SakuraNbtTest {
    @Test
    public void legacyCompoundListKeepsOrderAndValues() throws Exception {
        CompoundTag root = TagParser.parseCompoundFully("{records:[{rewarded:1b},{rewarded:0b}]}");
        ListTag records = SakuraNbt.compoundListOrEmpty(root, "records");
        assertEquals(2, records.size());
        assertTrue(records.getCompound(0).orElseThrow().getBooleanOr("rewarded", false));
        assertFalse(records.getCompound(1).orElseThrow().getBooleanOr("rewarded", true));
    }

    @Test
    public void typedReadsRejectMixedOrScalarRows() {
        CompoundTag root = new CompoundTag();
        ListTag mixed = new ListTag();
        mixed.add(new CompoundTag());
        mixed.add(StringTag.valueOf("corrupt"));
        root.put("records", mixed);
        root.putString("scalar", "corrupt");
        assertFalse(SakuraNbt.isCompoundList(mixed));
        assertTrue(SakuraNbt.compoundListOrEmpty(root, "records").isEmpty());
        assertTrue(SakuraNbt.compoundListOrEmpty(root, "scalar").isEmpty());
    }
}
