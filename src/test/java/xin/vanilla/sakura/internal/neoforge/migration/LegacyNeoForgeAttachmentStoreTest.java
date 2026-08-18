package xin.vanilla.sakura.internal.neoforge.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 验证旧玩家文件只移除 Sakura Attachment，其他数据保持原样。
 */
public class LegacyNeoForgeAttachmentStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void backsUpExactNodeAndRemovesOnlySakuraAttachment() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path vanillaPlayerData = temporaryFolder.newFolder("playerdata").toPath();
        Path worldData = temporaryFolder.newFolder("vanilla.xin").toPath();
        File playerFile = vanillaPlayerData.resolve(uuid + ".dat").toFile();

        CompoundTag sakura = new CompoundTag();
        sakura.putInt("signInCard", 12);
        CompoundTag other = new CompoundTag();
        other.putString("owner", "other_mod");
        CompoundTag attachments = new CompoundTag();
        attachments.put(LegacyNeoForgeAttachmentStore.ATTACHMENT_KEY, sakura);
        attachments.put("other_mod:data", other);
        CompoundTag root = new CompoundTag();
        root.putString("Dimension", "minecraft:overworld");
        root.put(LegacyNeoForgeAttachmentStore.ATTACHMENTS_KEY, attachments);
        NbtIo.writeCompressed(root, playerFile.toPath());

        LegacyNeoForgeAttachmentStore store = new LegacyNeoForgeAttachmentStore(vanillaPlayerData, worldData);
        Optional<CompoundTag> loaded = store.read(uuid);
        assertTrue(loaded.isPresent());

        String backup = store.backupAndVerify(uuid, loaded.get());
        assertTrue(store.backupMatches(backup, sakura));
        store.removeAndVerify(uuid, loaded.get());

        CompoundTag rewritten = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
        assertEquals("minecraft:overworld", rewritten.getString("Dimension"));
        assertFalse(rewritten.getCompound(LegacyNeoForgeAttachmentStore.ATTACHMENTS_KEY).contains(
                LegacyNeoForgeAttachmentStore.ATTACHMENT_KEY, 10
        ));
        assertEquals(other, rewritten.getCompound(
                LegacyNeoForgeAttachmentStore.ATTACHMENTS_KEY).getCompound("other_mod:data"));
    }

    @Test
    public void restoresRollbackWhenInterruptedTargetIsUnreadable() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path vanillaPlayerData = temporaryFolder.newFolder("interrupted-playerdata").toPath();
        Path worldData = temporaryFolder.newFolder("interrupted-vanilla.xin").toPath();
        Path playerFile = vanillaPlayerData.resolve(uuid + ".dat");
        Path rollbackFile = vanillaPlayerData.resolve(uuid + ".dat.sakura-migration.rollback");

        CompoundTag capability = new CompoundTag();
        capability.putInt("signInCard", 7);
        CompoundTag attachments = new CompoundTag();
        attachments.put(LegacyNeoForgeAttachmentStore.ATTACHMENT_KEY, capability);
        CompoundTag root = new CompoundTag();
        root.put(LegacyNeoForgeAttachmentStore.ATTACHMENTS_KEY, attachments);
        NbtIo.writeCompressed(root, rollbackFile);
        Files.write(playerFile, new byte[]{1, 2, 3});

        LegacyNeoForgeAttachmentStore store = new LegacyNeoForgeAttachmentStore(vanillaPlayerData, worldData);
        Optional<CompoundTag> restored = store.read(uuid);

        assertTrue(restored.isPresent());
        assertEquals(capability, restored.get());
        assertFalse(Files.exists(rollbackFile));
    }
}
