package xin.vanilla.sakura.api.reward;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 跨版本稳定的奖励类型命名空间 ID。
 */
@Getter
@EqualsAndHashCode
public final class RewardTypeId implements Comparable<RewardTypeId>, Serializable {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final String namespace;
    private final String path;

    private RewardTypeId(String namespace, String path) {
        this.namespace = validate(namespace, NAMESPACE, "namespace");
        this.path = validate(path, PATH, "path");
    }

    public static RewardTypeId of(String namespace, String path) {
        return new RewardTypeId(namespace, path);
    }

    public static RewardTypeId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Reward type id must use namespace:path: " + value);
        }
        return of(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(RewardTypeId other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    private static String validate(String value, Pattern pattern, String part) {
        Objects.requireNonNull(value, part);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid reward type " + part + ": " + value);
        }
        return value;
    }
}
