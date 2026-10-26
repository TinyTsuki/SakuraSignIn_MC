package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;
import java.util.*;
import java.util.function.*;

public final class LotteryRevealUiSession implements UiPageSession {
    public record Slot(int index, double x, double y, double width, double height, boolean face, boolean active) { }
    private final LotteryRevealPacket packet;
    private final LotteryAnimationStyle style;
    private final LongSupplier millis;
    private final BooleanSupplier connection;
    private final IntSupplier inventoryKey;
    private final Runnable claim;
    private final long openedAt;
    private final List<Reward> winners, preview;
    private List<UiGameElement> winnerIcons, candidateIcons;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private long binding;
    private boolean skipped, claimRequested, closed, resultsShown;
    private Boolean displayedReveal;

    public LotteryRevealUiSession(LotteryRevealPacket packet, LotteryAnimationStyle style, LongSupplier millis,
                                  BooleanSupplier connection, IntSupplier inventoryKey, Runnable claim) {
        this.packet = Objects.requireNonNull(packet); this.style = Objects.requireNonNull(style);
        this.millis = Objects.requireNonNull(millis); this.connection = Objects.requireNonNull(connection);
        this.inventoryKey = Objects.requireNonNull(inventoryKey); this.claim = Objects.requireNonNull(claim);
        openedAt = millis.getAsLong(); winners = packet.getWinners(); preview = packet.getPreview();
    }
    public String operationId() { return packet.getOperationId(); }
    public long elapsed() { return Math.max(0, millis.getAsLong() - openedAt); }
    public static long duration(LotteryAnimationStyle style) { return style == LotteryAnimationStyle.INSTANT ? 0 : style == LotteryAnimationStyle.CARDS ? 4800 : 6800; }
    public boolean revealed() { return skipped || elapsed() >= duration(style); }
    public void skip() { if (!closed && connection.getAsBoolean()) { skipped = true; requestClaim(); } }
    public void requestClaim() {
        if (claimRequested || closed || !connection.getAsBoolean()) return;
        claimRequested = true; claim.run();
    }
    public boolean claimRequested() { return claimRequested; }
    private boolean live(UiView owner, long token) { return !closed && view == owner && binding == token && connection.getAsBoolean(); }
    @Override public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        LotteryUiRewards.enter(next);
        long token = binding;
        next.text("title", SakuraComponent.get().literal(packet.getPoolName()));
        next.tooltip("title", SakuraComponent.get().literal(packet.getPoolName()));
        bindings.add(next.onAction("action", () -> { if (live(next, token)) action(); }));
        prepareIcons(); resultsShown = false; displayedReveal = null; draw();
    }
    private void prepareIcons() {
        if (winnerIcons != null) return;
        winnerIcons = winners.stream().map(RewardRenderer::uiIcon).toList();
        candidateIcons = new ArrayList<>();
        for (int i = 0; i < 48; i++) candidateIcons.add(packet.isPreviewVisible() && !preview.isEmpty()
                ? RewardRenderer.uiIcon(preview.get(i % preview.size())) : hidden());
    }
    private static UiGameElement hidden() { return null; }
    private void action() {
        if (revealed()) view.requestClose();
        else { skip(); draw(); }
    }
    private void draw() {
        if (view == null || view.nativeView() || !connection.getAsBoolean()) return;
        boolean revealed = revealed();
        if (displayedReveal == null || displayedReveal != revealed) {
            displayedReveal = revealed;
            view.text("action", SakuraComponent.get().transClient("word", revealed ? "confirm" : "lottery_skip_animation"));
            view.enabled("scene", !revealed); view.enabled("content", revealed);
        }
        if (revealed) {
            if (resultsShown) return;
            resultsShown = true;
            for (int i = 0; i < 48; i++) view.gameElement("slot" + i, null, new ScreenCoordinate(0, 0, 0, 0));
            for (int i = 0; i < 7; i++) view.gameElement("card" + i, null, new ScreenCoordinate(0, 0, 0, 0));
            view.gameElement("marker", null, new ScreenCoordinate(0, 0, 0, 0));
            String summary = winners.size() == 1 ? SakuraComponent.get().translateClient("format", "lottery_won_s", name(winners.get(0)))
                    : SakuraComponent.get().translateClient("format", "lottery_won_count_s", winners.size());
            view.text("summary", SakuraComponent.get().literal(summary)); view.tooltip("summary", SakuraComponent.get().literal(summary));
            UiView owner = view; long token = binding;
            LotteryUiRewards.bind(view, winners, packet.getOperationId(), () -> live(owner, token),
                    LotteryRevealUiSession::name, true, false, winnerIcons::get);
            return;
        }
        ScreenCoordinate area = view.bounds("scene");
        if (area == null || !Double.isFinite(area.width()) || !Double.isFinite(area.height()) || area.width() <= 0 || area.height() <= 0) return;
        List<Slot> slots = frame(style, elapsed(), area.width(), area.height(), preview.size());
        int winner = style == LotteryAnimationStyle.STRIP ? 43 : slots.size() - 1;
        ScreenCoordinate marker = new ScreenCoordinate(0, 0, 0, 0);
        for (Slot slot : slots) {
            boolean visible = slot.x() >= 0 && slot.y() >= 0 && slot.x() + slot.width() <= area.width() && slot.y() + slot.height() <= area.height();
            UiGameElement icon = !slot.face() ? hidden() : slot.index() == winner && packet.isPreviewVisible() ? winnerIcons.get(0) : candidateIcons.get(slot.index());
            view.gameElement("slot" + slot.index(), icon, visible ? new ScreenCoordinate(slot.x(), slot.y(), slot.width(), slot.height()) : new ScreenCoordinate(0, 0, 0, 0));
            view.text("slot" + slot.index(), SakuraComponent.get().literal(icon == null ? "?" : ""));
            if (slot.active() && visible) marker = new ScreenCoordinate(slot.x() - 3, slot.y() - 3, slot.width() + 6, slot.height() + 6);
        }
        for (int i = slots.size(); i < 48; i++) view.gameElement("slot" + i, null, new ScreenCoordinate(0, 0, 0, 0));
        for (int i = 0; i < 7; i++) {
            Slot card = style == LotteryAnimationStyle.CARDS && i < slots.size() ? slots.get(i) : null;
            view.gameElement("card" + i, null, card == null ? new ScreenCoordinate(0, 0, 0, 0)
                    : new ScreenCoordinate(card.x() - 9, card.y() - 19, 34, 54));
            if (card != null) view.enabled("card" + i, card.face());
        }
        if (style == LotteryAnimationStyle.STRIP) marker = new ScreenCoordinate(area.width() / 2 - 13, area.height() / 2 - 16, 26, 32);
        view.gameElement("marker", null, marker);
    }
    private static String name(Reward reward) {
        var client = net.minecraft.client.Minecraft.getInstance();
        return SakuraRewardClient.displayName(reward, client == null ? "en_us" : client.options.languageCode, true).toString();
    }
    public static List<Slot> frame(LotteryAnimationStyle style, long elapsed, double width, double height, int previews) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return List.of();
        elapsed = Math.max(0, elapsed);
        List<Slot> result = new ArrayList<>();
        if (style == LotteryAnimationStyle.STRIP) {
            double offset = (1 - Math.pow(1 - Math.min(1, elapsed / 6500D), 5)) * 43 * 38;
            double period = 48 * 38;
            for (int i = 0; i < 48; i++) {
                double distance = i * 38 - offset;
                distance -= Math.floor((distance + period / 2) / period) * period;
                result.add(new Slot(i, Math.round(width / 2 + distance - 8), height / 2 - 8, 16, 16, true, i == 43));
            }
        } else if (style == LotteryAnimationStyle.CARDS) {
            int count = Math.min(7, Math.max(1, (int) width / 42)), active = Math.min(count - 1, (int) (elapsed / 520));
            for (int i = 0; i < count; i++) result.add(new Slot(i, (width - count * 42 + 8) / 2 + i * 42 + 9, height / 2 - 8, 16, 16, i <= active, i == active));
        } else if (style == LotteryAnimationStyle.ROULETTE) {
            if (width < 24 || height < 24) return List.of();
            int desired = Math.max(6, Math.min(12, previews));
            int columns = 0, rows = 0;
            double spanX = width - 24, spanY = height - 24;
            for (int count = desired / 2 * 2; count >= 4 && columns == 0; count -= 2) {
                double best = Double.POSITIVE_INFINITY;
                for (int x = 2; x <= count / 2; x++) {
                    int y = (count + 4) / 2 - x;
                    if (y < 2 || (x - 1) * 32 > spanX || (y - 1) * 32 > spanY) continue;
                    double score = Math.abs(Math.log((spanX / (x - 1)) / (spanY / (y - 1))));
                    if (score < best) { best = score; columns = x; rows = y; }
                }
            }
            List<double[]> positions = new ArrayList<>();
            if (columns > 0) {
                double dx = spanX / (columns - 1), dy = spanY / (rows - 1);
                for (int x = 0; x < columns; x++) positions.add(new double[]{4 + x * dx, 4});
                for (int y = 1; y < rows; y++) positions.add(new double[]{4 + spanX, 4 + y * dy});
                for (int x = columns - 2; x >= 0; x--) positions.add(new double[]{4 + x * dx, 4 + spanY});
                for (int y = rows - 2; y >= 1; y--) positions.add(new double[]{4, 4 + y * dy});
            } else {
                boolean horizontal = spanX >= spanY;
                int count = Math.min(desired, 1 + (int) (Math.max(spanX, spanY) / 32));
                for (int i = 0; i < count; i++) {
                    double offset = (i - (count - 1) / 2D) * 32;
                    positions.add(new double[]{width / 2 - 8 + (horizontal ? offset : 0), height / 2 - 8 + (horizontal ? 0 : offset)});
                }
            }
            int count = positions.size(), winner = count - 1;
            double progress = Math.min(1, elapsed / 6500D), eased = 1 - Math.pow(1 - progress, 4);
            int active = progress >= 1 ? winner : (int) Math.floor(eased * (count * 7 + winner)) % count;
            for (int i = 0; i < count; i++) {
                double[] position = positions.get(i);
                result.add(new Slot(i, position[0], position[1], 16, 16, true, i == active));
            }
        }
        return List.copyOf(result);
    }
    @Override public void tick() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        if (revealed()) requestClaim();
        draw();
    }
    @Override public boolean key(int key, int scan, int modifiers, boolean pressed) {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean() || !pressed || key != 256 && key != inventoryKey.getAsInt()) return false;
        action(); return true;
    }
    public boolean requestClose() { if (view == null || closed) return false; view.requestClose(); return true; }
    @Override public void detach(UiDetachReason reason) {
        LotteryUiRewards.leave(view);
        binding++; view = null;
        for (AutoCloseable resource : bindings) try { resource.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        bindings.clear();
    }
    @Override public void close(UiCloseReason reason) {
        if (closed) return;
        if (reason != UiCloseReason.DISCONNECT) requestClaim();
        detach(UiDetachReason.DISCONNECT); closed = true;
    }
}
