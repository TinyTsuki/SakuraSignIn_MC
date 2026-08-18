package xin.vanilla.sakura.internal.neoforge.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 压缩 NBT 的单文件原子替换工具。
 */
public final class AtomicNbtFiles {
    private AtomicNbtFiles() {
    }

    public static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    public static void write(Path target, CompoundTag tag) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, temporary);
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
