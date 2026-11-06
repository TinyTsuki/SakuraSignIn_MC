package xin.vanilla.sakura.data.lottery;

import java.nio.ByteBuffer;
import java.util.Base64;

public final class LotteryPng {
    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_SIDE = 1600;
    private static final long SIGNATURE = 0x89504e470d0a1a0aL;

    private LotteryPng() {
    }

    public static int inspect(String encoded) {
        if (encoded == null || encoded.length() > ((MAX_BYTES + 2) / 3) * 4)
            throw new IllegalArgumentException("Lottery image is too large");
        return encoded.isEmpty() ? 0 : inspect(Base64.getDecoder().decode(encoded));
    }

    public static int inspect(byte[] data) {
        if (data.length < 45 || data.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid lottery image size");
        ByteBuffer header = ByteBuffer.wrap(data);
        if (header.getLong() != SIGNATURE || header.getInt() != 13 || header.getInt() != 0x49484452)
            throw new IllegalArgumentException("Expected PNG");
        int width = header.getInt(), height = header.getInt();
        if (width < 1 || width > MAX_SIDE || height < 1 || height > MAX_SIDE)
            throw new IllegalArgumentException("Invalid lottery image dimensions");
        header.position(8);
        boolean pixels = false;
        while (header.remaining() >= 12) {
            int size = header.getInt();
            if (size < 0 || size > header.remaining() - 8) break;
            int offset = header.position();
            int type = header.getInt();
            if (type == 0x49484452 && offset != 12) break;
            header.position(header.position() + size);
            var crc = new java.util.zip.CRC32();
            crc.update(data, offset, size + 4);
            if ((int) crc.getValue() != header.getInt()) break;
            if (type == 0x49444154 && size > 0) pixels = true;
            if (type == 0x49454e44 && size == 0 && pixels && !header.hasRemaining()) return width * height;
        }
        throw new IllegalArgumentException("Invalid PNG chunks");
    }
}
