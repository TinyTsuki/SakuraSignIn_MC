package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.enums.EnumAlignment;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.gui.ConfirmDialogScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientBootstrap;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.client.gui.RewardOperationWidget;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.enums.ESignInStatus;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.text.SakuraComponent;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.GLFWKeyHelper;
import xin.vanilla.sakura.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.DOWN_ARROW;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.INFO;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.LEFT_ARROW;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.RIGHT_ARROW;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.THEME_CHAOS_BUTTON;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.THEME_CLOVER_BUTTON;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.THEME_MAPLE_BUTTON;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.THEME_ORIGINAL_BUTTON;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.THEME_SAKURA_BUTTON;
import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.UP_ARROW;

/**
 * 签到日历主界面。Sakura 只保留主题与签到业务，交互生命周期由 Banira 管理。
 */
public final class SignInScreen extends BaniraScreen {
    public static int lastOffset = 6;
    public static int nextOffset = 6;

    private static final int COLUMNS = 7;
    private static final int ROWS = 6;

    private final List<SignInCell> signInCells = new ArrayList<>();
    private final Map<Integer, RewardOperationWidget> operationWidgets = new HashMap<>();

    private boolean showOpeningTips = Boolean.TRUE.equals(
            ClientConfig.get().display().showSignInScreenTips());
    private Text tips = Text.empty();
    private double scale = 1;
    private double aspectRatio;
    private int bgHeight;
    private int bgWidth;
    private int bgX;
    private int bgY;

    @Getter
    enum OperationButtonType {
        LEFT_ARROW(1),
        RIGHT_ARROW(2),
        UP_ARROW(3),
        DOWN_ARROW(4),
        INFO(5),
        THEME_ORIGINAL_BUTTON(100, "original"),
        THEME_SAKURA_BUTTON(101, "sakura"),
        THEME_CLOVER_BUTTON(102, "clover"),
        THEME_MAPLE_BUTTON(103, "maple"),
        THEME_CHAOS_BUTTON(104, "chaos");

        private final int code;
        private final String themeId;

        OperationButtonType(int code) {
            this(code, "");
        }

        OperationButtonType(int code, String themeId) {
            this.code = code;
            this.themeId = themeId;
        }

        static OperationButtonType fromCode(int code) {
            return Arrays.stream(values())
                    .filter(value -> value.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public SignInScreen() {
        super(SakuraComponent.get().transClient("title", "sign_in_title"));
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void onInit() {
        if (SakuraClientState.getCalendarCurrentDate() == null) {
            SakuraClientState.setCalendarCurrentDate(
                    RewardManager.getCompensateDate(DateUtils.getClientDate()));
        }
        ClientEventHandler.loadThemeTexture();
        tips = Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.sign_in_screen_tips");
        updateLayoutMetrics();
    }

    @Override
    protected void initWidgets() {
        operationWidgets.clear();
        signInCells.clear();
        createOperationWidgets();
        createCalendarCells(SakuraClientState.getCalendarCurrentDate());
        createOpeningTipButtons();
    }

    private void updateLayoutMetrics() {
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        aspectRatio = texture.getBgUV().getUWidth() / texture.getBgUV().getVHeight();
        bgHeight = Math.max(height - 20, 120);
        bgWidth = (int) Math.max(bgHeight * aspectRatio, 100);
        bgX = (width - bgWidth) / 2;
        bgY = 0;
        scale = bgHeight / texture.getBgUV().getVHeight();
    }

    private void refreshLayout() {
        updateLayoutMetrics();
        refreshWidget();
    }

    private void refreshTextureAndLayout() {
        ClientEventHandler.loadThemeTexture();
        refreshLayout();
    }

    private void createOperationWidgets() {
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        registerOperation(createTextureOperation(LEFT_ARROW, texture.getLeftArrowCoordinate(),
                texture.getArrowUV(), texture.getArrowHoverUV(), texture.getArrowTapUV())
                .setFlipHorizontal(true)
                .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.use_s_key", "←")));
        registerOperation(createTextureOperation(RIGHT_ARROW, texture.getRightArrowCoordinate(),
                texture.getArrowUV(), texture.getArrowHoverUV(), texture.getArrowTapUV())
                .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.use_s_key", "→")));
        registerOperation(createTextureOperation(UP_ARROW, texture.getUpArrowCoordinate(),
                texture.getArrowUV(), texture.getArrowHoverUV(), texture.getArrowTapUV())
                .setRotatedAngle(270)
                .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.use_s_key", "↑")));
        registerOperation(createTextureOperation(DOWN_ARROW, texture.getDownArrowCoordinate(),
                texture.getArrowUV(), texture.getArrowHoverUV(), texture.getArrowTapUV())
                .setRotatedAngle(90)
                .setFlipVertical(true)
                .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.use_s_key", "↓")));
        registerOperation(createTextureOperation(INFO, texture.getSignInInfoCoordinate(),
                texture.getSignInInfoUV(), texture.getSignInInfoUV(), texture.getSignInInfoUV()));

        registerThemeOperation(THEME_ORIGINAL_BUTTON);
        registerThemeOperation(THEME_SAKURA_BUTTON);
        registerThemeOperation(THEME_CLOVER_BUTTON);
        registerThemeOperation(THEME_MAPLE_BUTTON);
        registerThemeOperation(THEME_CHAOS_BUTTON);
        operationWidgets.get(THEME_CHAOS_BUTTON.code)
                .setTremblingAmplitude(3.5)
                .setTooltip(Text.trans(SakuraSignIn.MODID,
                        "tips.sakura_sign_in.click_to_change_theme")
                        .align(EnumAlignment.CENTER));
    }

    private RewardOperationWidget createTextureOperation(OperationButtonType type, Coordinate bounds,
                                                         Coordinate normal, Coordinate hover,
                                                         Coordinate pressed) {
        RewardOperationWidget widget = new RewardOperationWidget(this, type.code, SakuraClientState.getThemeTexture())
                .setNormal(copyCoordinate(normal))
                .setHover(copyCoordinate(hover))
                .setPressed(copyCoordinate(pressed))
                .setTextureWidth(SakuraClientState.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraClientState.getThemeTextureCoordinate().getTotalHeight())
                .setTransparentCheck(true)
                .setBounds(bounds, bgX, bgY, scale);
        widget.setReleaseHandler(event -> handleOperation(widget, event.button()));
        return widget;
    }

    private void registerThemeOperation(OperationButtonType type) {
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        RewardOperationWidget widget = createTextureOperation(type, texture.getThemeCoordinate(),
                texture.getThemeUV(), texture.getThemeHoverUV(), texture.getThemeTapUV())
                .setTooltip(Text.trans(SakuraSignIn.MODID,
                        "tips.sakura_sign_in.click_to_change_theme"));
        registerOperation(widget);
    }

    private void registerOperation(RewardOperationWidget widget) {
        operationWidgets.put(widget.getOperation(), widget);
        addWidget(widget);
    }

    private static Coordinate copyCoordinate(Coordinate source) {
        return new Coordinate()
                .setX(source.getX()).setY(source.getY())
                .setWidth(source.getWidth()).setHeight(source.getHeight())
                .setU0(source.getU0()).setV0(source.getV0())
                .setUWidth(source.getUWidth()).setVHeight(source.getVHeight());
    }

    private static Coordinate offsetU(Coordinate source, double offset) {
        return copyCoordinate(source).setU0(source.getU0() + offset);
    }

    private void createOpeningTipButtons() {
        tips.font(font);
        int textWidth = Math.max(120, AbstractGuiUtils.multilineTextWidth(tips));
        int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
        int buttonWidth = Math.min(100, Math.max(50, textWidth / 2 - 5));
        int x = (width - textWidth) / 2;
        int y = (height - textHeight - 24) / 2;

        ButtonWidget confirm = new ButtonWidget(this);
        confirm.id("opening-tip-confirm");
        confirm.bounds(new ScreenCoordinate(x, y + textHeight + 4, buttonWidth, 20));
        confirm.text(SakuraComponent.get().transClient("option", "confirm"));
        confirm.visible(showOpeningTips);
        confirm.onClick(button -> dismissOpeningTips(false));
        addWidget(confirm);

        ButtonWidget noReminder = new ButtonWidget(this);
        noReminder.id("opening-tip-no-reminder");
        noReminder.bounds(new ScreenCoordinate(x + textWidth - buttonWidth,
                y + textHeight + 4, buttonWidth, 20));
        noReminder.text(SakuraComponent.get().transClient("option", "no_remind"));
        noReminder.visible(showOpeningTips);
        noReminder.onClick(button -> dismissOpeningTips(true));
        addWidget(noReminder);
    }

    private void dismissOpeningTips(boolean persist) {
        showOpeningTips = false;
        if (persist) {
            ClientConfig.get().display().showSignInScreenTips(false);
            ClientConfig.save();
        }
        refreshWidget();
    }

    /**
     * 根据当前月份重建格子，切换月份时无需维护额外的鼠标状态。
     */
    private void createCalendarCells(Date current) {
        double startX = bgX + SakuraClientState.getThemeTextureCoordinate()
                .getCellCoordinate().getX() * scale;
        double startY = bgY + SakuraClientState.getThemeTextureCoordinate()
                .getCellCoordinate().getY() * scale;
        Date compensateDate = RewardManager.getCompensateDate(DateUtils.getClientDate());
        Date lastMonth = DateUtils.addMonth(current, -1);
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        int monthStartWeekDay = DateUtils.getDayOfWeekOfMonthStart(current);
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(current);

        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IPlayerSignInData signInData = SakuraPlayerData.get(player);
        Map<Integer, RewardList> monthRewards = player.hasPermissions(
                CommonConfig.get().permission().permissionRewardDetail())
                ? RewardManager.getMonthRewardList(current, signInData, lastOffset, nextOffset)
                : new HashMap<>();
        boolean allCurrentDaysDisplayed = false;
        boolean showLastReward = ClientConfig.get().display().showLastReward();
        boolean showNextReward = ClientConfig.get().display().showNextReward();

        for (int row = 0; row < ROWS; row++) {
            if (allCurrentDaysDisplayed && !showNextReward) {
                break;
            }
            for (int column = 0; column < COLUMNS; column++) {
                int itemIndex = row * COLUMNS + column;
                if (itemIndex >= 40) {
                    break;
                }
                double x = startX + column * (SakuraClientState.getThemeTextureCoordinate()
                        .getCellCoordinate().getWidth()
                        + SakuraClientState.getThemeTextureCoordinate().getCellHMargin()) * scale;
                double y = startY + row * (SakuraClientState.getThemeTextureCoordinate()
                        .getCellCoordinate().getHeight()
                        + SakuraClientState.getThemeTextureCoordinate().getCellVMargin()) * scale;
                int currentPoint = (monthStartWeekDay
                        - (SakuraClientState.getThemeTextureCoordinate().getWeekStart() - 1) + 6) % 7;
                CalendarCellData data = resolveCellData(itemIndex, currentPoint,
                        daysOfCurrentMonth, daysOfLastMonth, current, lastMonth,
                        compensateDate, showLastReward, showNextReward);
                allCurrentDaysDisplayed |= data.lastCurrentDay;

                int dateKey = data.year * 10000 + data.month * 100 + data.day;
                Date cellDate = DateUtils.getDate(dateKey);
                data.status = resolveCellStatus(signInData, compensateDate, cellDate,
                        dateKey, data.status);

                SignInCell cell = new SignInCell(this, SakuraClientState.getThemeTexture(),
                        SakuraClientState.getThemeTextureCoordinate(), x, y,
                        SakuraClientState.getThemeTextureCoordinate().getCellCoordinate().getWidth() * scale,
                        SakuraClientState.getThemeTextureCoordinate().getCellCoordinate().getHeight() * scale,
                        scale, monthRewards.getOrDefault(dateKey, new RewardList()),
                        data.year, data.month, data.day, data.status)
                        .setShowIcon(data.showIcon)
                        .setShowText(data.showText)
                        .setShowHover(data.showHover);
                cell.setReleaseHandler(event -> handleSignIn(event.button(), cell, player));
                cell.visible(!showOpeningTips);
                signInCells.add(cell);
                addWidget(cell);
            }
        }
    }

    private CalendarCellData resolveCellData(int index, int currentPoint,
                                             int daysOfCurrentMonth, int daysOfLastMonth,
                                             Date current, Date lastMonth, Date compensateDate,
                                             boolean showLastReward, boolean showNextReward) {
        CalendarCellData data = new CalendarCellData();
        data.showText = true;
        data.status = ESignInStatus.NO_ACTION.getCode();
        if (index >= currentPoint + daysOfCurrentMonth) {
            Date nextMonth = DateUtils.addMonth(current, 1);
            data.year = DateUtils.getYearPart(nextMonth);
            data.month = DateUtils.getMonthOfDate(nextMonth);
            data.day = index - currentPoint - daysOfCurrentMonth + 1;
            data.showIcon = showNextReward && data.day < lastOffset;
            data.showHover = data.showIcon;
        } else if (index < currentPoint) {
            data.year = DateUtils.getYearPart(lastMonth);
            data.month = DateUtils.getMonthOfDate(lastMonth);
            data.day = daysOfLastMonth - currentPoint + index + 1;
            data.showIcon = showLastReward && data.day > daysOfLastMonth - lastOffset;
            data.showHover = data.showIcon;
        } else {
            data.year = DateUtils.getYearPart(current);
            data.month = DateUtils.getMonthOfDate(current);
            data.day = index - currentPoint + 1;
            if (data.year == DateUtils.getYearPart(compensateDate)
                    && data.month == DateUtils.getMonthOfDate(compensateDate)
                    && data.day == DateUtils.getDayOfMonth(compensateDate)) {
                data.status = ESignInStatus.NOT_SIGNED_IN.getCode();
            }
            data.showIcon = true;
            data.showHover = true;
            data.lastCurrentDay = data.day == daysOfCurrentMonth;
        }
        return data;
    }

    private int resolveCellStatus(IPlayerSignInData signInData, Date compensateDate,
                                  Date cellDate, int dateKey, int status) {
        if (CommonConfig.get().makeUp().signInCard()) {
            Date minDate = DateUtils.addDay(compensateDate,
                    -CommonConfig.get().makeUp().reSignInDays());
            if (DateUtils.toDateInt(minDate) <= dateKey
                    && dateKey <= DateUtils.toDateInt(compensateDate)
                    && status != ESignInStatus.NOT_SIGNED_IN.getCode()) {
                status = ESignInStatus.CAN_REPAIR.getCode();
            }
        }
        if (RewardManager.isRewarded(signInData, cellDate, false)) {
            return ESignInStatus.REWARDED.getCode();
        }
        if (RewardManager.isSignedIn(signInData, cellDate, false)) {
            return ESignInStatus.SIGNED_IN.getCode();
        }
        return status;
    }

    private void handleOperation(RewardOperationWidget widget, int mouseButton) {
        if (showOpeningTips) {
            return;
        }
        OperationButtonType type = OperationButtonType.fromCode(widget.getOperation());
        if (type == null) {
            return;
        }
        if (type == LEFT_ARROW && mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            changeCalendar(DateUtils.addMonth(SakuraClientState.getCalendarCurrentDate(), -1));
        } else if (type == RIGHT_ARROW && mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            changeCalendar(DateUtils.addMonth(SakuraClientState.getCalendarCurrentDate(), 1));
        } else if (type == UP_ARROW && mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            changeCalendar(DateUtils.addYear(SakuraClientState.getCalendarCurrentDate(), -1));
        } else if (type == DOWN_ARROW && mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            changeCalendar(DateUtils.addYear(SakuraClientState.getCalendarCurrentDate(), 1));
        } else if (type.code >= THEME_ORIGINAL_BUTTON.code
                && type.code <= THEME_CHAOS_BUTTON.code
                && (mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_LEFT
                || mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT)) {
            selectBuiltInTheme(type, mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT);
        }
    }

    private void changeCalendar(Date date) {
        SakuraClientState.setCalendarCurrentDate(date);
        SakuraNetwork.requestMonth(date);
        refreshLayout();
    }

    private void selectBuiltInTheme(OperationButtonType type, boolean specialVersion) {
        ClientConfig.get().display().themeId(type.themeId);
        ClientConfig.get().display().specialVariant(specialVersion);
        ClientConfig.save();
        refreshTextureAndLayout();
    }

    private void handleSignIn(int button, SignInCell cell, ClientPlayerEntity player) {
        if (showOpeningTips || cell == null || button != GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        Date cellDate = DateUtils.getDate(cell.getYear(), cell.getMonth(), cell.getDay());
        if (cell.getStatus() == ESignInStatus.NOT_SIGNED_IN.getCode()) {
            if (RewardManager.getCompensateDateInt()
                    < DateUtils.toDateInt(RewardManager.getCompensateDate(DateUtils.getClientDate()))) {
                SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                        "message", "next_day_cannot_operate"), SakuraNotificationTypes.SIGN_IN);
            } else {
                cell.setStatus(ClientConfig.get().display().autoRewarded()
                        ? ESignInStatus.REWARDED.getCode()
                        : ESignInStatus.SIGNED_IN.getCode());
                SakuraNetwork.sendToServer(new SignInPacket(
                        DateUtils.toDateTimeString(DateUtils.getClientDate()),
                        ClientConfig.get().display().autoRewarded(), ESignInType.SIGN_IN));
            }
        } else if (cell.getStatus() == ESignInStatus.SIGNED_IN.getCode()) {
            if (RewardManager.isRewarded(SakuraPlayerData.get(player), cellDate, false)) {
                SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                        "message", "already_get_reward"), SakuraNotificationTypes.SIGN_IN);
            } else {
                cell.setStatus(ESignInStatus.REWARDED.getCode());
                SakuraNetwork.sendToServer(new SignInPacket(DateUtils.toDateTimeString(cellDate),
                        ClientConfig.get().display().autoRewarded(), ESignInType.REWARD));
            }
        } else if (cell.getStatus() == ESignInStatus.CAN_REPAIR.getCode()) {
            requestMakeUpSignIn(cell, cellDate, player);
        } else if (cell.getStatus() == ESignInStatus.NO_ACTION.getCode()) {
            String key = cellDate.after(RewardManager.getCompensateDate(DateUtils.getClientDate()))
                    ? "next_day_cannot_operate" : "past_day_cannot_operate";
            SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                    "message", key), SakuraNotificationTypes.SIGN_IN);
        } else if (cell.getStatus() == ESignInStatus.REWARDED.getCode()) {
            SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                    "message", "already_get_reward"), SakuraNotificationTypes.SIGN_IN);
        } else {
            Component component = SakuraComponent.get().literal(
                    ESignInStatus.valueOf(cell.getStatus()).getDescription()
                            + ": " + DateUtils.toString(cellDate));
            SakuraClientNotifications.error(component, SakuraNotificationTypes.SIGN_IN);
        }
    }

    private void requestMakeUpSignIn(SignInCell cell, Date cellDate, ClientPlayerEntity player) {
        if (!CommonConfig.get().makeUp().signInCard()) {
            SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                    "message", "server_not_enable_sign_in_card"), SakuraNotificationTypes.SIGN_IN);
            return;
        }
        if (SakuraPlayerData.get(player).getSignInCard() <= 0) {
            SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                    "message", "not_enough_sign_in_card"), SakuraNotificationTypes.SIGN_IN);
            return;
        }
        Minecraft.getInstance().setScreen(new ConfirmDialogScreen(
                new ConfirmDialogScreen.Args()
                        .parentScreen(this)
                        .title(SakuraComponent.get().transClient("title", "confirm_operation"))
                        .message(SakuraComponent.get().transClient("tips", "confirm_make_up_sign_in",
                                DateUtils.toString(cellDate)))
                        .onConfirm(() -> {
                            cell.setStatus(ClientConfig.get().display().autoRewarded()
                                    ? ESignInStatus.REWARDED.getCode()
                                    : ESignInStatus.SIGNED_IN.getCode());
                            SakuraNetwork.sendToServer(new SignInPacket(
                                    DateUtils.toDateTimeString(cellDate),
                                    ClientConfig.get().display().autoRewarded(),
                                    ESignInType.RE_SIGN_IN));
                        })
        ));
    }

    @Override
    protected void onRender(MatrixStack stack, float partialTicks) {
        renderBackground(stack);
        renderBackgroundTexture(stack);
        renderCalendarTitle(stack);
        updateOperationPresentation();

        if (showOpeningTips) {
            renderOpeningTips(stack);
        }
        renderWidgets(stack, partialTicks);

        if (!showOpeningTips) {
            addDeferredTooltipRender(this::renderHoveredTooltips);
        }
    }

    private void renderBackgroundTexture(MatrixStack stack) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getTextureManager().bind(SakuraClientState.getThemeTexture());
        Coordinate uv = SakuraClientState.getThemeTextureCoordinate().getBgUV();
        AbstractGuiUtils.blit(stack, bgX, bgY, bgWidth, bgHeight,
                (float) uv.getU0(), (float) uv.getV0(),
                (int) uv.getUWidth(), (int) uv.getVHeight(),
                SakuraClientState.getThemeTextureCoordinate().getTotalWidth(),
                SakuraClientState.getThemeTextureCoordinate().getTotalHeight());
        RenderSystem.disableBlend();
    }

    private void renderCalendarTitle(MatrixStack stack) {
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        double yearX = bgX + texture.getYearCoordinate().getX() * scale;
        double yearY = bgY + texture.getYearCoordinate().getY() * scale;
        double monthX = bgX + texture.getMonthCoordinate().getX() * scale;
        double monthY = bgY + texture.getMonthCoordinate().getY() * scale;
        String language = Minecraft.getInstance().options.languageCode;
        font.draw(stack, DateUtils.toLocalStringYear(SakuraClientState.getCalendarCurrentDate(), language),
                (float) yearX, (float) yearY, texture.getTextColorDate());
        font.draw(stack, DateUtils.toLocalStringMonth(SakuraClientState.getCalendarCurrentDate(), language),
                (float) monthX, (float) monthY, texture.getTextColorDate());
    }

    private void updateOperationPresentation() {
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        String language = Minecraft.getInstance().options.languageCode;
        String yearTitle = DateUtils.toLocalStringYear(SakuraClientState.getCalendarCurrentDate(), language);
        String monthTitle = DateUtils.toLocalStringMonth(SakuraClientState.getCalendarCurrentDate(), language);
        double yearX = bgX + texture.getYearCoordinate().getX() * scale;
        double monthX = bgX + texture.getMonthCoordinate().getX() * scale;

        updateArrowBounds(LEFT_ARROW, texture.getLeftArrowCoordinate(),
                (monthX - bgX - 1) / scale - font.lineHeight / scale,
                texture.getLeftArrowCoordinate().getY());
        updateArrowBounds(RIGHT_ARROW, texture.getRightArrowCoordinate(),
                (monthX - bgX + font.width(monthTitle) + 1) / scale,
                texture.getRightArrowCoordinate().getY());
        updateArrowBounds(UP_ARROW, texture.getUpArrowCoordinate(),
                (yearX - bgX - 1) / scale - font.lineHeight / scale,
                texture.getUpArrowCoordinate().getY());
        updateArrowBounds(DOWN_ARROW, texture.getDownArrowCoordinate(),
                (yearX - bgX + font.width(yearTitle) + 1) / scale,
                texture.getDownArrowCoordinate().getY());

        RewardOperationWidget info = operationWidgets.get(INFO.code);
        if (info != null) {
            info.setRotatedAngle(info.hovered() ? 10 : 0);
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                IPlayerSignInData data = SakuraPlayerData.get(player);
                info.setTooltip(Text.trans(SakuraSignIn.MODID,
                        "tips.sakura_sign_in.sign_in_info",
                        data.getSignInCard(), data.getContinuousSignInDays(), data.getTotalSignInDays()));
            }
        }

        for (OperationButtonType type : OperationButtonType.values()) {
            RewardOperationWidget widget = operationWidgets.get(type.code);
            if (widget == null) {
                continue;
            }
            widget.visible(!showOpeningTips);
            if (type.code < THEME_ORIGINAL_BUTTON.code) {
                continue;
            }
            int index = type.code - THEME_ORIGINAL_BUTTON.code;
            double uOffset = index * texture.getThemeUV().getUWidth();
            boolean selected = SakuraClientState.getActiveThemeId().equals(type.themeId);
            Coordinate normal = selected ? texture.getThemeTapUV() : texture.getThemeUV();
            Coordinate hover = selected ? texture.getThemeTapUV() : texture.getThemeHoverUV();
            widget.setNormal(offsetU(normal, uOffset))
                    .setHover(offsetU(hover, uOffset))
                    .setPressed(offsetU(texture.getThemeTapUV(), uOffset))
                    .setBounds(new Coordinate()
                                    .setX(index * (texture.getThemeCoordinate().getWidth()
                                            + texture.getThemeHMargin()) + texture.getThemeCoordinate().getX())
                                    .setY(texture.getThemeCoordinate().getY())
                                    .setWidth(texture.getThemeCoordinate().getWidth())
                                    .setHeight(texture.getThemeCoordinate().getHeight()),
                            bgX, bgY, scale);
        }
    }

    private void updateArrowBounds(OperationButtonType type, Coordinate source, double x, double y) {
        RewardOperationWidget widget = operationWidgets.get(type.code);
        if (widget == null) {
            return;
        }
        Coordinate titleCoordinate = type == LEFT_ARROW || type == RIGHT_ARROW
                ? SakuraClientState.getThemeTextureCoordinate().getMonthCoordinate()
                : SakuraClientState.getThemeTextureCoordinate().getYearCoordinate();
        if (source.getX() != titleCoordinate.getX() || source.getY() != titleCoordinate.getY()) {
            x = source.getX();
            y = source.getY();
        }
        double width = source.getWidth();
        double height = source.getHeight();
        if ((type == LEFT_ARROW || type == RIGHT_ARROW)
                && width == SakuraClientState.getThemeTextureCoordinate().getMonthCoordinate().getWidth()
                && height == SakuraClientState.getThemeTextureCoordinate().getMonthCoordinate().getHeight()) {
            width = font.lineHeight / scale;
            height = font.lineHeight / scale;
        } else if ((type == UP_ARROW || type == DOWN_ARROW)
                && width == SakuraClientState.getThemeTextureCoordinate().getYearCoordinate().getWidth()
                && height == SakuraClientState.getThemeTextureCoordinate().getYearCoordinate().getHeight()) {
            width = font.lineHeight / scale;
            height = font.lineHeight / scale;
        }
        widget.setBounds(new Coordinate().setX(x).setY(y).setWidth(width).setHeight(height),
                bgX, bgY, scale);
    }

    private void renderOpeningTips(MatrixStack stack) {
        ShapeDrawArgs.RectParams rect = new ShapeDrawArgs.RectParams()
                .x(4).y(4).width(width - 8).height(height - 8).radius(15);
        BaseShapeWidget.drawShape(new ShapeDrawArgs()
                .stack(stack)
                .type(ShapeDrawArgs.ShapeType.RECT)
                .color(0xDD000000)
                .rect(rect));
        tips.stack(stack).font(font);
        int textWidth = Math.max(120, AbstractGuiUtils.multilineTextWidth(tips));
        int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
        AbstractGuiUtils.drawString(tips,
                (width - textWidth) / 2.0f,
                (height - textHeight - 24) / 2.0f);
    }

    private void renderHoveredTooltips(MatrixStack stack) {
        if (!popupOption.isEmpty()) {
            return;
        }
        boolean showRewardDetail = Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.hasPermissions(
                CommonConfig.get().permission().permissionRewardDetail());
        if (showRewardDetail) {
            for (SignInCell cell : signInCells) {
                if (!cell.isShowHover() || !cell.isMouseInside(inputState.mouseX(), inputState.mouseY())) {
                    continue;
                }
                if (inputState.onlyShiftPressed()) {
                    AbstractGuiUtils.drawPopupMessage(
                            Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.how_to_sign_in")
                                    .stack(stack).font(font).align(EnumAlignment.CENTER),
                            (int) inputState.mouseX(), (int) inputState.mouseY(), width, height);
                } else {
                    cell.renderTooltip(stack, font, itemRenderer);
                }
                break;
            }
        }
        for (RewardOperationWidget widget : operationWidgets.values()) {
            widget.renderTooltip(stack, inputState.mouseX(), inputState.mouseY());
        }
    }

    @Override
    protected void onKeyPressed(KeyPressedHandleArgs eventArgs) {
        int keyCode = eventArgs.keyCode();
        if (keyCode == GLFWKey.GLFW_KEY_ESCAPE
                || keyCode == SakuraClientBootstrap.getSignInKey().currentKey()
                || keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            if (showOpeningTips) {
                dismissOpeningTips(false);
            } else {
                onClose();
            }
            eventArgs.consumed(true);
        }
    }

    @Override
    protected void onKeyReleased(KeyReleasedHandleArgs eventArgs) {
        int keyCode = eventArgs.keyCode();
        if (matchesKey(ClientConfig.get().signKeys().lastMonth(), keyCode)) {
            changeCalendar(DateUtils.addMonth(SakuraClientState.getCalendarCurrentDate(), -1));
        } else if (matchesKey(ClientConfig.get().signKeys().nextMonth(), keyCode)) {
            changeCalendar(DateUtils.addMonth(SakuraClientState.getCalendarCurrentDate(), 1));
        } else if (matchesKey(ClientConfig.get().signKeys().lastYear(), keyCode)) {
            changeCalendar(DateUtils.addYear(SakuraClientState.getCalendarCurrentDate(), -1));
        } else if (matchesKey(ClientConfig.get().signKeys().nextYear(), keyCode)) {
            changeCalendar(DateUtils.addYear(SakuraClientState.getCalendarCurrentDate(), 1));
        } else {
            return;
        }
        eventArgs.consumed(true);
    }

    private boolean matchesKey(List<String> bindings, int keyCode) {
        return bindings.stream().anyMatch(binding -> GLFWKeyHelper.matchKey(binding, keyCode));
    }

    private static final class CalendarCellData {
        private int year;
        private int month;
        private int day;
        private int status;
        private boolean showIcon;
        private boolean showText;
        private boolean showHover;
        private boolean lastCurrentDay;
    }
}
