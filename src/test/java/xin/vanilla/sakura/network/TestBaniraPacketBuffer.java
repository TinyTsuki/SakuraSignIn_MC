package xin.vanilla.sakura.network;

import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 纯 Java 包往返测试使用的顺序缓冲区。
 */
public final class TestBaniraPacketBuffer implements BaniraPacketBuffer {
    private final List<Object> values = new ArrayList<>();
    private int readIndex;

    @Override
    public int readVarInt() {
        return (Integer) next();
    }

    @Override
    public void writeVarInt(int value) {
        values.add(value);
    }

    @Override
    public String readUtf() {
        return (String) next();
    }

    @Override
    public String readUtf(int maxLength) {
        return readUtf();
    }

    @Override
    public void writeUtf(String value) {
        values.add(value);
    }

    @Override
    public void writeUtf(String value, int maxLength) {
        writeUtf(value);
    }

    @Override
    public int readInt() {
        return (Integer) next();
    }

    @Override
    public void writeInt(int value) {
        values.add(value);
    }

    @Override
    public long readLong() {
        return (Long) next();
    }

    @Override
    public void writeLong(long value) {
        values.add(value);
    }

    @Override
    public boolean readBoolean() {
        return (Boolean) next();
    }

    @Override
    public void writeBoolean(boolean value) {
        values.add(value);
    }

    @Override
    public byte readByte() {
        return (Byte) next();
    }

    @Override
    public void writeByte(int value) {
        values.add((byte) value);
    }

    @Override
    public double readDouble() {
        return (Double) next();
    }

    @Override
    public void writeDouble(double value) {
        values.add(value);
    }

    @Override
    public UUID readUuid() {
        return (UUID) next();
    }

    @Override
    public void writeUuid(UUID value) {
        values.add(value);
    }

    @Override
    public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return enumClass.cast(next());
    }

    @Override
    public void writeEnum(Enum<?> value) {
        values.add(value);
    }

    @Override
    public BaniraIdentifier readIdentifier() {
        return (BaniraIdentifier) next();
    }

    @Override
    public void writeIdentifier(BaniraIdentifier value) {
        values.add(value);
    }

    private Object next() {
        return values.get(readIndex++);
    }
}
