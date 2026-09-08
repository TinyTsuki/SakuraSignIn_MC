package xin.vanilla.sakura.internal.client.dev;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.widget.IWidget;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardListEntryWidget;
import xin.vanilla.sakura.client.gui.RewardOperationWidget;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInCell;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.event.ClientEventHandler;

import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Dev-only render instrumentation. It does not move the OS cursor or replace Banira's screen-post flush. */
final class SakuraNetworkSmokeScreens {
    private static java.lang.reflect.Method renderInputUpdate;
    private final Metrics calendarMetrics;
    private final Metrics rewardMetrics;
    private final String previousTheme;
    private final boolean previousVariant;
    private final boolean previousOpeningTips;
    private final boolean previousSidebar;
    private final Date previousDate;
    private final Date baseDate;
    private CalendarView calendar;
    private RewardView rewards;
    private Screen active;
    private Metrics activeMetrics;
    private long framesAtChange;
    private boolean closed;
    private int monthRequests;
    private int scrollEvents;

    SakuraNetworkSmokeScreens(BooleanSupplier sampling) {
        if (!SakuraNetworkSmokeStatus.enabled()) throw new IllegalStateException("Client smoke is disabled");
        calendarMetrics = new Metrics("sign-in", sampling);
        rewardMetrics = new Metrics("reward-editor", sampling);
        previousTheme = ClientConfig.get().display().themeId();
        previousVariant = ClientConfig.get().display().specialVariant();
        previousOpeningTips = ClientConfig.get().display().showSignInScreenTips();
        previousSidebar = SakuraClientState.isRewardOptionBarOpened();
        previousDate = SakuraClientState.getCalendarCurrentDate();
        baseDate = RewardManager.getCompensateDate(SakuraClock.clientNow());
    }

    void open(Minecraft client) {
        ClientConfig.get().display().showSignInScreenTips(false);
        SakuraClientState.setCalendarCurrentDate(baseDate);
        SakuraClientState.setRewardOptionBarOpened(true);
        calendar = new CalendarView(calendarMetrics);
        rewards = new RewardView(rewardMetrics);
        show(client, calendar, calendarMetrics);
    }

    boolean readyForCycle(Minecraft client) {
        if (client.screen != active) throw new IllegalStateException("Smoke UI was replaced before completion");
        return activeMetrics.frames > framesAtChange;
    }

    void runCycle(Minecraft client, int cycle) {
        boolean signIn = (cycle & 1) != 0;
        Metrics metrics = signIn ? calendarMetrics : rewardMetrics;
        long startedAt = System.nanoTime();
        int pair = (cycle - 1) / 2;
        List<String> themes = BuiltInThemeCatalog.themeIds();
        ClientConfig.get().display().themeId(themes.get(pair % themes.size())).specialVariant(false);
        ClientEventHandler.loadThemeTexture();
        if (signIn) {
            Date month = DateUtils.addMonth(baseDate, -(pair % 12));
            SakuraClientState.setCalendarCurrentDate(month);
            show(client, calendar, metrics);
            SakuraNetwork.requestMonth(month);
            monthRequests++;
        } else {
            SakuraClientState.setRewardOptionBarOpened(true);
            show(client, rewards, metrics);
            rewards.selectBaseRewards();
            // Drive the real scroll handler. A short reward list may clamp the offset to zero.
            rewards.mouseScrolled(rewards.width / 2.0D, rewards.height / 2.0D,
                    (pair & 1) == 0 ? -3.0D : 3.0D);
            rewards.mouseScrolled(8.0D, rewards.getFont().lineHeight + 16.0D,
                    (pair & 1) == 0 ? -1.0D : 1.0D);
            scrollEvents += 2;
        }
        metrics.contentCycle = cycle;
        metrics.update(System.nanoTime() - startedAt);
    }

    private void show(Minecraft client, Screen screen, Metrics metrics) {
        active = screen;
        activeMetrics = metrics;
        framesAtChange = metrics.frames;
        client.setScreen(screen);
    }

    boolean verified() {
        return calendarMetrics.contentFrames > 0 && rewardMetrics.contentFrames > 0
                && calendarMetrics.renderedCycles > 0 && rewardMetrics.renderedCycles > 0
                && calendarMetrics.renderedCycles + rewardMetrics.renderedCycles >= 20;
    }

    String summary() {
        return calendarMetrics.summary() + " " + rewardMetrics.summary()
                + " month-requests=" + monthRequests + " scroll-events=" + scrollEvents
                + " render-timing=cpu-side-wall-clock excludes=screen-post-flush,gpu"
                + " calendar-instrumentation=final-screen-delegate";
    }

    void close(Minecraft client) {
        if (closed) return;
        closed = true;
        if (client.screen == active) client.setScreen(null);
        ClientConfig.get().display().themeId(previousTheme).specialVariant(previousVariant)
                .showSignInScreenTips(previousOpeningTips);
        SakuraClientState.setRewardOptionBarOpened(previousSidebar);
        SakuraClientState.setCalendarCurrentDate(previousDate);
        ClientEventHandler.loadThemeTexture();
    }

    /** SignInScreen is final. This dev BaniraScreen delegates its lifecycle without modifying production APIs. */
    private static final class CalendarView extends BaniraScreen {
        private final SignInScreen delegate;
        private final Metrics metrics;
        private IPlayerSignInData lastSummary;
        private Object lastRecords;

        private CalendarView(Metrics metrics) {
            this(new SignInScreen(), metrics);
        }

        private CalendarView(SignInScreen delegate, Metrics metrics) {
            super(delegate.getTitle());
            this.delegate = delegate;
            this.metrics = metrics;
            season(delegate.season());
            theme(delegate.theme());
        }

        @Override
        protected void init() {
            delegate.init(minecraft, width, height);
        }

        @Override
        protected void initWidgets() {
        }

        @Override
        protected void onRender(PoseStack stack, float partialTicks) {
        }

        @Override
        public void tick() {
            delegate.tick();
            IPlayerSignInData data = SakuraPlayerData.get(minecraft.player);
            // The production packet handler refreshes only instanceof SignInScreen, so mirror that refresh here.
            if (data != lastSummary || data.getSignInRecords() != lastRecords) {
                delegate.init(minecraft, width, height);
                lastSummary = data;
                lastRecords = data.getSignInRecords();
            }
        }

        @Override
        public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            IWidget hovered = contentWidget(delegate, true);
            int x = hoverX(hovered, mouseX);
            int y = hoverY(hovered, mouseY);
            // Change only the in-memory render input; the real cursor and the normal deferred flush are untouched.
            updateRenderInput(delegate, x, y);
            try {
                long startedAt = System.nanoTime();
                boolean sampledAtStart = metrics.sampling.getAsBoolean();
                delegate.render(stack, x, y, partialTicks);
                metrics.render(System.nanoTime() - startedAt, hovered != null, sampledAtStart);
            } finally {
                updateRenderInput(delegate, mouseX, mouseY);
            }
        }

        @Override
        public void removed() {
            delegate.removed();
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return delegate.mouseClicked(x, y, button);
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            return delegate.mouseReleased(x, y, button);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double delta) {
            return delegate.mouseScrolled(x, y, delta);
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            return delegate.keyPressed(key, scan, modifiers);
        }

        @Override
        public boolean keyReleased(int key, int scan, int modifiers) {
            return delegate.keyReleased(key, scan, modifiers);
        }
    }

    private static final class RewardView extends RewardOptionScreen {
        private final Metrics metrics;

        private RewardView(Metrics metrics) {
            this.metrics = metrics;
        }

        private void selectBaseRewards() {
            for (IWidget widget : widgets()) {
                if (widget instanceof RewardOperationWidget
                        && ((RewardOperationWidget) widget).getOperation() == 201) {
                    RewardOperationWidget rule = (RewardOperationWidget) widget;
                    double x = rule.realX() + rule.realWidth() / 2.0D;
                    double y = rule.realY() + rule.realHeight() / 2.0D;
                    mouseClicked(x, y, 0);
                    mouseReleased(x, y, 0);
                    return;
                }
            }
            throw new IllegalStateException("Reward editor has no base reward rule widget");
        }

        @Override
        public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            IWidget hovered = contentWidget(this, false);
            int x = hoverX(hovered, mouseX);
            int y = hoverY(hovered, mouseY);
            updateRenderInput(this, x, y);
            try {
                long startedAt = System.nanoTime();
                boolean sampledAtStart = metrics.sampling.getAsBoolean();
                super.render(stack, x, y, partialTicks);
                metrics.render(System.nanoTime() - startedAt, hovered != null, sampledAtStart);
            } finally {
                updateRenderInput(this, mouseX, mouseY);
            }
        }
    }

    // Dev-only access to the existing runtime updater; replaying render-pre would clear deferred tooltips.
    private static void updateRenderInput(BaniraScreen screen, double mouseX, double mouseY) {
        try {
            Object state = screen.inputState();
            if (renderInputUpdate == null) {
                renderInputUpdate = state.getClass().getMethod("handleDrawScreenPre", double.class, double.class);
            }
            renderInputUpdate.invoke(state, mouseX, mouseY);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update smoke render input", exception);
        }
    }

    private static IWidget contentWidget(BaniraScreen screen, boolean calendar) {
        for (IWidget widget : screen.widgets()) {
            boolean content = calendar ? widget instanceof SignInCell && ((SignInCell) widget).isShowHover()
                    : widget instanceof RewardListEntryWidget;
            if (content && widget.visible() && widget.bounds().width() > 0 && widget.bounds().height() > 0
                    && hoverX(widget, 0) > 0 && hoverX(widget, 0) < screen.width
                    && hoverY(widget, 0) > 0 && hoverY(widget, 0) < screen.height) return widget;
        }
        return null;
    }

    private static int hoverX(IWidget widget, int fallback) {
        return widget == null ? fallback : (int) (widget.absoluteX() + widget.bounds().width() / 2.0D);
    }

    private static int hoverY(IWidget widget, int fallback) {
        return widget == null ? fallback : (int) (widget.absoluteY() + widget.bounds().height() / 2.0D);
    }

    static final class Metrics {
        private final String view;
        private final BooleanSupplier sampling;
        private long frames;
        private long contentFrames;
        private long renderTotalNanos;
        private long renderMaxNanos;
        private long updateTotalNanos;
        private long updateMaxNanos;
        private int updates;
        private int contentCycle;
        private int lastRenderedCycle;
        private int renderedCycles;

        Metrics(String view, BooleanSupplier sampling) {
            this.view = view;
            this.sampling = sampling;
        }

        private void update(long elapsed) {
            if (!sampling.getAsBoolean()) return;
            updates++;
            updateTotalNanos += elapsed;
            updateMaxNanos = Math.max(updateMaxNanos, elapsed);
        }

        void render(long elapsed, boolean content, boolean sampledAtStart) {
            if (!sampledAtStart || !sampling.getAsBoolean()) return;
            frames++;
            renderTotalNanos += elapsed;
            renderMaxNanos = Math.max(renderMaxNanos, elapsed);
            if (content) {
                contentFrames++;
                if (contentCycle > 0 && contentCycle != lastRenderedCycle) {
                    renderedCycles++;
                    lastRenderedCycle = contentCycle;
                }
            }
        }

        String summary() {
            return view + "-cycles=" + renderedCycles + " " + view + "-render-frames=" + frames
                    + " " + view + "-content-render-frames=" + contentFrames
                    + " " + view + "-render-cpu-average-ns=" + (frames == 0 ? 0L : renderTotalNanos / frames)
                    + " " + view + "-render-cpu-max-ns=" + renderMaxNanos
                    + " " + view + "-update-average-ns=" + (updates == 0 ? 0L : updateTotalNanos / updates)
                    + " " + view + "-update-max-ns=" + updateMaxNanos;
        }
    }
}
