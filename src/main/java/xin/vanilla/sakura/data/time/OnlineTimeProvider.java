package xin.vanilla.sakura.data.time;

@FunctionalInterface
public interface OnlineTimeProvider {
    int playTicks(Object player);
}
