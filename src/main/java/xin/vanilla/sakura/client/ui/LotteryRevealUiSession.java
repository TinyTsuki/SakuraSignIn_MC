package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class LotteryRevealUiSession implements UiPageSession {
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    public static class Slot {
        int index;
        double x;
        double y;
        double width;
        double height;
        boolean face;
        boolean active;

    }

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
    private Boolean displayedReveal, displayedConnection;
    private int resultColumns;

    public LotteryRevealUiSession(LotteryRevealPacket packet, LotteryAnimationStyle style, LongSupplier millis,
                                  BooleanSupplier connection, IntSupplier inventoryKey, Runnable claim) {
        this.packet = Objects.requireNonNull(packet);
        this.style = Objects.requireNonNull(style);
        this.millis = Objects.requireNonNull(millis);
        this.connection = Objects.requireNonNull(connection);
        this.inventoryKey = Objects.requireNonNull(inventoryKey);
        this.claim = Objects.requireNonNull(claim);
        openedAt = millis.getAsLong();
        winners = packet.getWinners();
        preview = packet.getPreview();
    }

    public String operationId() {
        return packet.getOperationId();
    }

    public long elapsed() {
        return Math.max(0, millis.getAsLong() - openedAt);
    }

    public static long duration(LotteryAnimationStyle style) {
        return style == LotteryAnimationStyle.INSTANT ? 0 : style == LotteryAnimationStyle.CARDS ? 4800 : 6800;
    }

    public boolean revealed() {
        return skipped || elapsed() >= duration(style);
    }

    public void skip() {
        if (!closed && connection.getAsBoolean()) {
            skipped = true;
            requestClaim();
        }
    }

    public void requestClaim() {
        if (claimRequested || closed || !connection.getAsBoolean()) return;
        claimRequested = true;
        claim.run();
    }

    public boolean claimRequested() {
        return claimRequested;
    }

    private boolean live(UiView owner, long token) {
        return !closed && view == owner && binding == token && connection.getAsBoolean();
    }

    @Override
    public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        long token = binding;
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.text("title", SakuraComponent.get().literal(packet.getPoolName()));
        next.tooltip("title", SakuraComponent.get().literal(packet.getPoolName()));
        next.text("subtitle", SakuraComponent.get().literal("SAKURA SIGN-IN · " + SakuraComponent.get().transClient("word", "lottery_result")));
        next.text("result_count", SakuraComponent.get().transClient("format", "aui_lottery_result_count_s", winners.size()));
        next.text("connection", SakuraComponent.get().transClient("word", "aui_lottery_disconnected"));
        next.gameElement("close", UiGameElement.preset(xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle.CLOSE));
        next.tooltip("close", xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close"));
        bindings.add(next.onAction("close", () -> {
            if (live(next, token)) next.requestClose();
        }));
        bindings.add(next.onAction("action", () -> {
            if (live(next, token)) action();
        }));
        prepareIcons();
        resultsShown = false;
        resultColumns = 0;
        displayedReveal = null;
        displayedConnection = null;
        draw();
    }

    private void prepareIcons() {
        if (winnerIcons != null) return;
        winnerIcons = winners.stream().map(RewardRenderer::uiIcon).collect(java.util.stream.Collectors.toList());
        candidateIcons = new ArrayList<>();
        for (int i = 0; i < 48; i++)
            candidateIcons.add(packet.isPreviewVisible() && !preview.isEmpty()
                    ? RewardRenderer.uiIcon(preview.get(i % preview.size())) : hidden());
    }

    private static UiGameElement hidden() {
        return null;
    }

    private void action() {
        if (revealed()) view.requestClose();
        else {
            skip();
            draw();
        }
    }

    private void draw() {
        if (view == null || view.nativeView()) return;
        boolean connected = connection.getAsBoolean();
        if (displayedConnection == null || displayedConnection != connected) {
            displayedConnection = connected;
            view.enabled("action", connected);
            view.enabled("close", connected);
            view.enabled("connection", !connected);
        }
        if (!connected) return;
        boolean revealed = revealed();
        if (displayedReveal == null || displayedReveal != revealed) {
            displayedReveal = revealed;
            view.text("action", SakuraComponent.get().transClient("word", revealed ? "confirm" : "lottery_skip_animation"));
            view.enabled("scene", !revealed);
            view.enabled("content", revealed && winners.size() != 1);
            view.enabled("result_single", revealed && winners.size() == 1);
            view.text("section", SakuraComponent.get().transClient("word", revealed ? "aui_lottery_rewards" : "lottery_drawing"));
        }
        if (revealed) {
            if (!resultsShown) {
                for (int i = 0; i < 48; i++) {
                    view.gameElement("slot" + i, null, new ScreenCoordinate(0, 0, 0, 0));
                    view.gameElement("tile" + i, null, new ScreenCoordinate(0, 0, 0, 0));
                }
                for (int i = 0; i < 7; i++) view.gameElement("card" + i, null, new ScreenCoordinate(0, 0, 0, 0));
                view.gameElement("marker", null, new ScreenCoordinate(0, 0, 0, 0));
                String summary = winners.size() == 1 ? SakuraComponent.get().translateClient("format", "lottery_won_s", name(winners.get(0)))
                        : SakuraComponent.get().translateClient("format", "lottery_won_count_s", winners.size());
                view.text("summary", SakuraComponent.get().literal(summary));
                view.tooltip("summary", SakuraComponent.get().literal(summary));
                if (winners.size() == 1) {
                    view.gameElement("single_icon", winnerIcons.get(0));
                    view.text("single_name", SakuraComponent.get().literal(name(winners.get(0))));
                    view.tooltip("single_name", SakuraComponent.get().literal(name(winners.get(0))));
                    view.text("single_type", SakuraComponent.get().literal(typeName(winners.get(0))));
                }
            }
            bindResults();
            return;
        }
        ScreenCoordinate area = view.bounds("scene");
        if (area == null || !Double.isFinite(area.width()) || !Double.isFinite(area.height()) || area.width() <= 0 || area.height() <= 0)
            return;
        List<Slot> slots = presentationFrame(style, elapsed(), area.width(), area.height(), preview.size());
        int winner = style == LotteryAnimationStyle.STRIP ? 43 : slots.size() - 1;
        ScreenCoordinate marker = new ScreenCoordinate(0, 0, 0, 0);
        for (Slot slot : slots) {
            boolean visible = slot.x() >= 0 && slot.y() >= 0 && slot.x() + slot.width() <= area.width() && slot.y() + slot.height() <= area.height();
            UiGameElement icon = !slot.face() ? hidden() : slot.index() == winner && packet.isPreviewVisible() ? winnerIcons.get(0) : candidateIcons.get(slot.index());
            view.gameElement("slot" + slot.index(), icon, visible ? new ScreenCoordinate(slot.x(), slot.y(), slot.width(), slot.height()) : new ScreenCoordinate(0, 0, 0, 0));
            view.text("slot" + slot.index(), SakuraComponent.get().literal(icon == null ? "?" : ""));
            boolean tile = visible && style != LotteryAnimationStyle.CARDS;
            view.gameElement("tile" + slot.index(), null, tile ? new ScreenCoordinate(slot.x() - 4, slot.y() - 8, 24, 32) : new ScreenCoordinate(0, 0, 0, 0));
            if (slot.active() && visible) marker = new ScreenCoordinate(slot.x() - 5, slot.y() - 9, 26, 34);
        }
        for (int i = slots.size(); i < 48; i++) {
            view.gameElement("slot" + i, null, new ScreenCoordinate(0, 0, 0, 0));
            view.gameElement("tile" + i, null, new ScreenCoordinate(0, 0, 0, 0));
        }
        for (int i = 0; i < 7; i++) {
            Slot card = style == LotteryAnimationStyle.CARDS && i < slots.size() ? slots.get(i) : null;
            view.gameElement("card" + i, null, card == null ? new ScreenCoordinate(0, 0, 0, 0)
                    : new ScreenCoordinate(card.x() - 4, card.y() - 8, 24, 32));
            if (card != null) view.enabled("card" + i, card.face());
        }
        if (style == LotteryAnimationStyle.STRIP)
            marker = new ScreenCoordinate(area.width() / 2 - 16, area.height() / 2 - 20, 32, 40);
        view.gameElement("marker", null, marker);
    }

    private void bindResults() {
        ScreenCoordinate area = view.bounds("result_area");
        if (area == null || !Double.isFinite(area.width()) || area.width() <= 0) return;
        int columns = winners.size() == 1 || area.width() < 240 ? 1 : area.width() < 360 ? 2 : 3;
        if (resultsShown && resultColumns == columns) return;
        if (view.state() != null) {
            double first = view.state().rowPosition() * Math.max(1, resultColumns);
            view.state().rowPosition(first / columns);
        }
        resultColumns = columns;
        resultsShown = true;
        UiView owner = view;
        long token = binding;
        for (int column = 0; column < 3; column++) {
            int offset = column;
            view.enabled("column" + column, column < columns);
            String id = column == 0 ? "fields" : column == 1 ? "fields_middle" : "fields_end";
            view.rows(id, new UiRowLayout() {
                public int rowHeight() {
                    return 60;
                }

                public String rowClass(int row) {
                    return "result-card";
                }

                public int size() {
                    return offset >= columns ? 0 : (winners.size() + columns - 1 - offset) / columns;
                }

                public String key(int row) {
                    return packet.getOperationId() + ":" + (row * columns + offset);
                }

                public void bind(UiView cell, int row) {
                    if (!live(owner, token) || columns != resultColumns) return;
                    int index = row * columns + offset;
                    Reward reward = winners.get(index);
                    cell.text("card", SakuraComponent.get().literal(""));
                    cell.gameElement("icon", winnerIcons.get(index));
                    cell.text("label", SakuraComponent.get().literal(name(reward)));
                    cell.text("type", SakuraComponent.get().literal(typeName(reward)));
                    cell.text("index", SakuraComponent.get().literal(String.format(Locale.ROOT, "%02d", index + 1)));
                    cell.tooltip("label", SakuraComponent.get().literal(name(reward)));
                    cell.tooltip("type", SakuraComponent.get().literal(typeName(reward)));
                    cell.tooltip("icon", SakuraComponent.get().literal(name(reward)));
                }
            }, 40);
        }
    }

    private static String typeName(Reward reward) {
        lombok.var extension = SakuraRewardClient.find(reward.getTypeId()).map(SakuraRewardClient.Registration::getExtension).orElse(null);
        return extension == null || extension.getTypeName() == null ? reward.getTypeId().toString() : extension.getTypeName().get().toString();
    }

    private static String name(Reward reward) {
        lombok.var client = net.minecraft.client.Minecraft.getInstance();
        return SakuraRewardClient.displayName(reward, client == null ? "en_us" : client.options.languageCode, true).toString();
    }

    public static List<Slot> presentationFrame(LotteryAnimationStyle style, long elapsed, double width, double height, int previews) {
        double px = style == LotteryAnimationStyle.ROULETTE ? Math.min(18, Math.max(0, (width - 32) / 2)) : 0;
        double py = style == LotteryAnimationStyle.ROULETTE ? Math.min(18, Math.max(0, (height - 32) / 2)) : 0;
        return frame(style, elapsed, width - px * 2, height - py * 2, previews).stream()
                .map(s -> new Slot(s.index(), s.x() + px, s.y() + py, s.width(), s.height(), s.face(), style != LotteryAnimationStyle.STRIP && s.active())).collect(java.util.stream.Collectors.toList());
    }

    public static List<Slot> frame(LotteryAnimationStyle style, long elapsed, double width, double height, int previews) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return com.google.common.collect.ImmutableList.of();
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
            for (int i = 0; i < count; i++)
                result.add(new Slot(i, (width - count * 42 + 8) / 2 + i * 42 + 9, height / 2 - 8, 16, 16, i <= active, i == active));
        } else if (style == LotteryAnimationStyle.ROULETTE) {
            if (width < 24 || height < 24) return com.google.common.collect.ImmutableList.of();
            int desired = Math.max(6, Math.min(12, previews));
            int columns = 0, rows = 0;
            double spanX = width - 24, spanY = height - 24;
            for (int count = desired / 2 * 2; count >= 4 && columns == 0; count -= 2) {
                double best = Double.POSITIVE_INFINITY;
                for (int x = 2; x <= count / 2; x++) {
                    int y = (count + 4) / 2 - x;
                    if (y < 2 || (x - 1) * 32 > spanX || (y - 1) * 32 > spanY) continue;
                    double score = Math.abs(Math.log((spanX / (x - 1)) / (spanY / (y - 1))));
                    if (score < best) {
                        best = score;
                        columns = x;
                        rows = y;
                    }
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
        return com.google.common.collect.ImmutableList.copyOf(result);
    }

    @Override
    public void tick() {
        if (view == null || view.nativeView() || closed) return;
        if (connection.getAsBoolean() && revealed()) requestClaim();
        draw();
    }

    @Override
    public boolean key(int key, int scan, int modifiers, boolean pressed) {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean() || !pressed || key != 256 && key != inventoryKey.getAsInt())
            return false;
        action();
        return true;
    }

    public boolean requestClose() {
        if (view == null || closed) return false;
        view.requestClose();
        return true;
    }

    @Override
    public void detach(UiDetachReason reason) {
        if (view != null && !view.nativeView() && view.state() != null && resultColumns > 0)
            view.state().rowPosition(view.state().rowPosition() * resultColumns);
        resultColumns = 0;
        binding++;
        view = null;
        for (AutoCloseable resource : bindings)
            try {
                resource.close();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        bindings.clear();
    }

    @Override
    public void close(UiCloseReason reason) {
        if (closed) return;
        if (reason != UiCloseReason.DISCONNECT) requestClaim();
        detach(UiDetachReason.DISCONNECT);
        closed = true;
    }
}
