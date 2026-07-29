package xin.vanilla.sakura.internal.forge.storage;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.File;
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

    public static CompoundNBT read(Path path) throws IOException {
        return CompressedStreamTools.readCompressed(path.toFile());
    }

    public static void write(Path target, CompoundNBT tag) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        File temporaryFile = temporary.toFile();
        CompressedStreamTools.writeCompressed(tag, temporaryFile);
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
