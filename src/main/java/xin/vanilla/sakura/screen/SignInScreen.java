package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumMCColor;
import xin.vanilla.sakura.enums.EnumSignInStatus;
import xin.vanilla.sakura.enums.EnumSignInType;
import xin.vanilla.sakura.event.ClientModEventHandler;
import xin.vanilla.sakura.network.packet.RewardCellRequestToServer;
import xin.vanilla.sakura.network.packet.SignInToServer;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.*;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.screen.theme.Theme;
import xin.vanilla.sakura.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static xin.vanilla.sakura.screen.SignInScreen.OperationButtonType.*;

@OnlyIn(Dist.CLIENT)
public class SignInScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    // region 变量定义

    /**
     * 父级 Screen
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    private Screen previousScreen;

    private final KeyEventManager keyManager = new KeyEventManager();

    private boolean SIGN_IN_SCREEN_TIPS = Boolean.TRUE.equals(ClientConfig.SHOW_SIGN_IN_SCREEN_TIPS.get());

    private Text tips;

    /**
     * 上月最后offset天
     */
    public static int lastOffset = 6;
    /**
     * 下月开始offset天
     */
    public static int nextOffset = 6;

    /**
     * 日历单元格集合
     */
    private final List<SignInCell> signInCells = new ArrayList<>();

    /**
     * 日历表格列数
     */
    private static final int columns = 7;
    /**
     * 日历表格行数
     */
    private static final int rows = 6;

    /**
     * UI缩放比例
     */
    private double scale = 1.0D;
    /**
     * 背景宽高比
     */
    private double aspectRatio = (double) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getUWidth() / SakuraSignIn.getThemeTextureCoordinate().getBgUV().getVHeight();

    // 背景渲染坐标大小定义
    private int bgH = Math.max(super.height - 20, 120);
    private int bgW = (int) Math.max(bgH * aspectRatio, 100);
    private int bgX = (super.width - bgW) / 2;
    private int bgY = 0;

    /**
     * 操作按钮集合
     */
    private final Map<Integer, OperationButton> BUTTONS = new HashMap<>();

    /**
     * 弹出层选项
     */
    private PopupOption popupOption;
    /**
     * 主题文件列表
     */
    private List<File> themeFileList;

    // endregion

    /**
     * 操作按钮类型
     */
    @Getter
    enum OperationButtonType {
        LEFT_ARROW(1),
        RIGHT_ARROW(2),
        UP_ARROW(3),
        DOWN_ARROW(4),
        INFO(5),
        THEME_ORIGINAL_BUTTON(100, "textures/gui/sign_in_calendar_original.png"),
        THEME_SAKURA_BUTTON(101, "textures/gui/sign_in_calendar_sakura.png"),
        THEME_CLOVER_BUTTON(102, "textures/gui/sign_in_calendar_clover.png"),
        THEME_MAPLE_BUTTON(103, "textures/gui/sign_in_calendar_maple.png"),
        THEME_CHAOS_BUTTON(104, "textures/gui/sign_in_calendar_chaos.png");

        final int code;
        final String path;

        OperationButtonType(int code) {
            this.code = code;
            path = "";
        }

        OperationButtonType(int code, String path) {
            this.code = code;
            this.path = path;
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    public SignInScreen() {
        super(Component.translatable(EnumI18nType.TITLE, "sign_in_title").toTextComponent(SakuraUtils.getClientLanguage()));
    }

    @Override
    protected void init() {
        super.init();
        this.popupOption = PopupOption.init(super.font);
        // 初始化材质及材质坐标信息
        this.updateTextureAndCoordinate();

        this.themeFileList = TextureUtils.getThemeFilesInDirectory(TextureUtils.CUSTOM_THEME_DIR);

        // 初始化奖励格子
        this.updateRewardCell();
        // 初始化布局信息
        this.updateLayout();

        tips = Text.translatable(EnumI18nType.TIPS, "sign_in_screen_tips");
        Button submit = AbstractGuiUtils.newButton(0, 0, 0, 20,
                Component.translatableClient(EnumI18nType.OPTION, "confirm"), button -> this.SIGN_IN_SCREEN_TIPS = false);
        Button notAgain = AbstractGuiUtils.newButton(0, 0, 0, 20,
                Component.translatableClient(EnumI18nType.OPTION, "no_remind"), button -> {
                    this.SIGN_IN_SCREEN_TIPS = false;
                    ClientConfig.SHOW_SIGN_IN_SCREEN_TIPS.set(false);
                });
        super.addButton(submit);
        super.addButton(notAgain);
    }

    /**
     * 更新材质及材质坐标信息
     */
    private void updateTextureAndCoordinate() {
        SakuraUtils.loadThemeTexture();
        // 更新按钮信息
        this.updateButtons();
    }

    /**
     * 更新按钮信息
     */
    private void updateButtons() {
        ResourceLocation texture = SakuraSignIn.getThemeTexture();
        TextureCoordinate textureCoordinate = SakuraSignIn.getThemeTextureCoordinate();
        BUTTONS.put(LEFT_ARROW.getCode(), new OperationButton(LEFT_ARROW.getCode(), texture)
                .setCoordinate(textureCoordinate.getLeftArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setFlipHorizontal(true)
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "use_s_key", "←"))
                .setKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT)));
        BUTTONS.put(RIGHT_ARROW.getCode(), new OperationButton(RIGHT_ARROW.getCode(), texture)
                .setCoordinate(textureCoordinate.getRightArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "use_s_key", "→"))
                .setKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT)));
        BUTTONS.put(UP_ARROW.getCode(), new OperationButton(UP_ARROW.getCode(), texture)
                .setCoordinate(textureCoordinate.getUpArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setRotatedAngle(270)
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "use_s_key", "↑"))
                .setKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT)));
        BUTTONS.put(DOWN_ARROW.getCode(), new OperationButton(DOWN_ARROW.getCode(), texture)
                .setCoordinate(textureCoordinate.getDownArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setRotatedAngle(90).setFlipVertical(true)
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "use_s_key", "↓"))
                .setKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT)));
        BUTTONS.put(INFO.getCode(), new OperationButton(INFO.getCode(), texture)
                .setCoordinate(textureCoordinate.getSignInInfoCoordinate())
                .setNormal(textureCoordinate.getSignInInfoUV()).setHover(textureCoordinate.getSignInInfoUV()).setTap(textureCoordinate.getSignInInfoUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTransparentCheck(true));

        BUTTONS.put(THEME_ORIGINAL_BUTTON.getCode(), new OperationButton(THEME_ORIGINAL_BUTTON.getCode(), texture)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "click_to_change_theme")));
        BUTTONS.put(THEME_SAKURA_BUTTON.getCode(), new OperationButton(THEME_SAKURA_BUTTON.getCode(), texture)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "click_to_change_theme")));
        BUTTONS.put(THEME_CLOVER_BUTTON.getCode(), new OperationButton(THEME_CLOVER_BUTTON.getCode(), texture)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "click_to_change_theme")));
        BUTTONS.put(THEME_MAPLE_BUTTON.getCode(), new OperationButton(THEME_MAPLE_BUTTON.getCode(), texture)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "click_to_change_theme")));
        BUTTONS.put(THEME_CHAOS_BUTTON.getCode(), new OperationButton(THEME_CHAOS_BUTTON.getCode(), texture)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTremblingAmplitude(3.5)
                .setTooltip(Text.translatable(EnumI18nType.TIPS, "click_to_change_theme_or_select_external_theme").setAlign(Text.Align.CENTER)));
    }

    /**
     * 计算并更新布局信息
     */
    private void updateLayout() {
        // 更新背景宽高比
        aspectRatio = (double) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getUWidth() / SakuraSignIn.getThemeTextureCoordinate().getBgUV().getVHeight();
        // 限制背景高度大于120
        bgH = Math.max(super.height - 20, 120);
        // 限制背景宽度大于100
        bgW = (int) Math.max(bgH * aspectRatio, 100);
        // 使背景水平居中
        bgX = (super.width - bgW) / 2;
        // 更新缩放比例
        this.scale = bgH * 1.0f / SakuraSignIn.getThemeTextureCoordinate().getBgUV().getVHeight();
        // 创建或更新格子位置
        this.createCalendarCells(SakuraSignIn.getCalendarCurrentDate());
    }

    private void updateRewardCell() {
        SakuraUtils.sendPacketToServer(new RewardCellRequestToServer(SakuraSignIn.getCalendarCurrentDate()));
    }

    /**
     * 创建日历格子
     */
    private void createCalendarCells(Date current) {
        // 清除原有格子，避免重复添加
        signInCells.clear();

        double startX = bgX + SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getX() * this.scale;
        double startY = bgY + SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getY() * this.scale;

        Date lastMonth = DateUtils.addMonth(current, -1);
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        int dayOfWeekOfMonthStart = DateUtils.getDayOfWeekOfMonthStart(current);
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(current);

        boolean allCurrentDaysDisplayed = false;
        boolean showLastReward = ClientConfig.SHOW_LAST_REWARD.get();
        boolean showNextReward = ClientConfig.SHOW_NEXT_REWARD.get();
        for (int row = 0; row < rows; row++) {
            if (allCurrentDaysDisplayed && !showNextReward) break;
            for (int col = 0; col < columns; col++) {
                // 计算当前格子的索引
                int itemIndex = row * columns + col;
                // 检查是否已超过设置显示上限
                if (itemIndex >= 40) break;
                double x = startX + col * (SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getWidth() + SakuraSignIn.getThemeTextureCoordinate().getCellHMargin()) * this.scale;
                double y = startY + row * (SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getHeight() + SakuraSignIn.getThemeTextureCoordinate().getCellVMargin()) * this.scale;
                int year, month, day;
                boolean showIcon, showText, showHover;
                // 计算本月第一天是第几(0为第一个)个格子
                int curPoint = (dayOfWeekOfMonthStart - (SakuraSignIn.getThemeTextureCoordinate().getWeekStart() - 1) + 6) % 7;
                // 根据itemIndex确定日期和状态
                if (itemIndex >= curPoint + daysOfCurrentMonth) {
                    // 属于下月的日期
                    year = DateUtils.getYearPart(DateUtils.addMonth(current, 1));
                    month = DateUtils.getMonthOfDate(DateUtils.addMonth(current, 1));
                    day = itemIndex - curPoint - daysOfCurrentMonth + 1;
                    showIcon = showNextReward && day < lastOffset;
                    showText = true;
                    showHover = showNextReward && day < lastOffset;
                } else if (itemIndex < curPoint) {
                    // 属于上月的日期
                    year = DateUtils.getYearPart(lastMonth);
                    month = DateUtils.getMonthOfDate(lastMonth);
                    day = daysOfLastMonth - curPoint + itemIndex + 1;
                    showIcon = showLastReward && day > daysOfLastMonth - lastOffset;
                    showText = true;
                    showHover = showLastReward && day > daysOfLastMonth - lastOffset;
                } else {
                    // 属于当前月的日期
                    year = DateUtils.getYearPart(current);
                    month = DateUtils.getMonthOfDate(current);
                    day = itemIndex - curPoint + 1;
                    showIcon = true;
                    showText = true;
                    showHover = true;
                    allCurrentDaysDisplayed = day == daysOfCurrentMonth;
                }

                // 创建物品格子
                SignInCell cell = new SignInCell(SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getWidth() * this.scale, SakuraSignIn.getThemeTextureCoordinate().getCellCoordinate().getHeight() * this.scale, this.scale, year, month, day);
                cell.setShowIcon(showIcon).setShowText(showText).setShowHover(showHover);
                // 添加到列表
                signInCells.add(cell);
            }
        }
    }

    /**
     * 绘制背景纹理
     */
    private void renderBackgroundTexture(MatrixStack matrixStack) {
        // 开启 OpenGL 的混合模式，使纹理透明区域渲染生效
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 绘制背景纹理，使用缩放后的宽度和高度
        AbstractGuiUtils.bindTexture(SakuraSignIn.getThemeTexture());
        AbstractGuiUtils.blit(matrixStack, bgX, bgY, bgW, bgH, (float) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getU0(), (float) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getV0(), (int) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getUWidth(), (int) SakuraSignIn.getThemeTextureCoordinate().getBgUV().getVHeight(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight());
        // 关闭 OpenGL 的混合模式
        RenderSystem.disableBlend();
    }

    /**
     * 处理操作按钮事件
     *
     * @param value         操作按钮
     * @param updateLayout  是否更新布局
     * @param updateTexture 是否更新纹理和坐标
     * @param flag          是否处理过事件
     */
    private void handleOperation(OperationButton value, AtomicBoolean updateLayout, AtomicBoolean updateRewardCell, AtomicBoolean updateTexture, AtomicBoolean flag) {
        if (this.SIGN_IN_SCREEN_TIPS) return;
        // 上个月
        if (value.getOperation() == LEFT_ARROW.getCode()) {
            if (ClientConfig.KEY_SIGN_LAST_MONTH.get().stream().anyMatch(keyManager::isMousePressed)) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), -1));
                updateLayout.set(true);
                updateRewardCell.set(true);
                flag.set(true);
            }
        }
        // 下个月
        else if (value.getOperation() == RIGHT_ARROW.getCode()) {
            if (ClientConfig.KEY_SIGN_NEXT_MONTH.get().stream().anyMatch(keyManager::isMousePressed)) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), 1));
                updateLayout.set(true);
                updateRewardCell.set(true);
                flag.set(true);
            }
        }
        // 上一年
        else if (value.getOperation() == UP_ARROW.getCode()) {
            if (ClientConfig.KEY_SIGN_LAST_YEAR.get().stream().anyMatch(keyManager::isMousePressed)) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), -1));
                updateLayout.set(true);
                updateRewardCell.set(true);
                flag.set(true);
            }
        }
        // 下一年
        else if (value.getOperation() == DOWN_ARROW.getCode()) {
            if (ClientConfig.KEY_SIGN_NEXT_YEAR.get().stream().anyMatch(keyManager::isMousePressed)) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), 1));
                updateLayout.set(true);
                updateRewardCell.set(true);
                flag.set(true);
            }
        }
        // 类原版主题
        else if (value.getOperation() == THEME_ORIGINAL_BUTTON.getCode()) {
            SakuraSignIn.setSpecialVersionTheme(keyManager.onlyMouseRightPressed());
            ClientConfig.THEME.set(THEME_ORIGINAL_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(SakuraSignIn.isSpecialVersionTheme());
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 樱花粉主题
        else if (value.getOperation() == THEME_SAKURA_BUTTON.getCode()) {
            SakuraSignIn.setSpecialVersionTheme(keyManager.onlyMouseRightPressed());
            ClientConfig.THEME.set(THEME_SAKURA_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(SakuraSignIn.isSpecialVersionTheme());
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 四叶草主题
        else if (value.getOperation() == THEME_CLOVER_BUTTON.getCode()) {
            SakuraSignIn.setSpecialVersionTheme(keyManager.onlyMouseRightPressed());
            ClientConfig.THEME.set(THEME_CLOVER_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(SakuraSignIn.isSpecialVersionTheme());
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 枫叶主题
        else if (value.getOperation() == THEME_MAPLE_BUTTON.getCode()) {
            SakuraSignIn.setSpecialVersionTheme(keyManager.onlyMouseRightPressed());
            ClientConfig.THEME.set(THEME_MAPLE_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(SakuraSignIn.isSpecialVersionTheme());
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 混沌主题
        else if (value.getOperation() == THEME_CHAOS_BUTTON.getCode()) {
            if (keyManager.onlyMouseLeftPressed()) {
                ClientConfig.THEME.set(THEME_CHAOS_BUTTON.getPath());
                updateLayout.set(true);
                updateTexture.set(true);
                flag.set(true);
            } else if (keyManager.onlyMouseRightPressed()) {
                // 绘制弹出层选项
                popupOption.clear();
                // 若文件夹为空, 绘制提示, 并在点击时打开主题文件夹
                popupOption.addOption(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "select_theme_file"));
                popupOption.addOption(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "create_theme_file"));
                popupOption.addOption(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "edit_theme_file"));
                if (CollectionUtils.isNullOrEmpty(themeFileList)) {
                    popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "theme_folder_empty").setColor(EnumMCColor.RED.getColor()));
                } else {
                    popupOption.addOption(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "open_theme_folder"));
                    popupOption.addOption(themeFileList.stream().map(file -> {
                        String name = file.getName();
                        name = name.endsWith(SakuraSignIn.THEME_FILE_SUFFIX) ? name.substring(0, name.length() - SakuraSignIn.THEME_FILE_SUFFIX.length()) : name;
                        return name;
                    }).toArray(String[]::new));
                }
                popupOption.setMaxWidth(AbstractGuiUtils.multilineTextWidth(Text.translatable(EnumI18nType.OPTION, "theme_folder_empty")))
                        .setMaxLines(7)
                        .build(super.font, keyManager.getMouseX(), keyManager.getMouseY(), String.format("主题选择按钮:%s", value.getOperation()));
            }
        }
    }

    private void handleSignIn(int button, SignInCell cell, ClientPlayerEntity player) {
        if (this.SIGN_IN_SCREEN_TIPS) return;
        Date cellDate = DateUtils.getDate(cell.year, cell.month, cell.day);
        int signStatusCode = cell.getRewardCellStatus();
        // 签到
        if (signStatusCode == EnumSignInStatus.NOT_SIGNED_IN.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                if (RewardManager.getCompensateDateInt() < DateUtils.toDateInt(RewardManager.getCompensateDate(DateUtils.getClientDate()))) {
                    Component component = Component.translatableClient(EnumI18nType.MESSAGE, "next_day_cannot_operate");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                } else {
                    SakuraUtils.sendPacketToServer(new SignInToServer(DateUtils.toDateTimeString(DateUtils.getClientDate()), ClientConfig.AUTO_REWARDED.get(), EnumSignInType.SIGN_IN));
                }
            }
        }
        // 领取奖励
        else if (signStatusCode == EnumSignInStatus.SIGNED_IN.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                Component component = Component.translatableClient(EnumI18nType.MESSAGE, "already_signed");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
            } else {
                if (RewardManager.isRewarded(PlayerSignInDataCapability.getData(player), cellDate, false)) {
                    Component component = Component.translatableClient(EnumI18nType.MESSAGE, "already_get_reward");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                } else {
                    SakuraUtils.sendPacketToServer(new SignInToServer(DateUtils.toDateTimeString(cellDate), ClientConfig.AUTO_REWARDED.get(), EnumSignInType.REWARD));
                }
            }
        }
        // 补签
        else if (signStatusCode == EnumSignInStatus.CAN_REPAIR.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                if (!ServerConfig.SIGN_IN_CARD.get()) {
                    Component component = Component.translatableClient(EnumI18nType.MESSAGE, "server_not_enable_sign_in_card");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                } else {
                    if (PlayerSignInDataCapability.getData(player).getSignInCard() <= 0) {
                        Component component = Component.translatableClient(EnumI18nType.MESSAGE, "not_enough_sign_in_card");
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                    } else {
                        SakuraUtils.sendPacketToServer(new SignInToServer(DateUtils.toDateTimeString(cellDate), ClientConfig.AUTO_REWARDED.get(), EnumSignInType.RE_SIGN_IN));
                    }
                }
            }
        } else if (signStatusCode == EnumSignInStatus.NO_ACTION.getCode()) {
            if (cellDate.after(RewardManager.getCompensateDate(DateUtils.getClientDate()))) {
                Component component = Component.translatableClient(EnumI18nType.MESSAGE, "next_day_cannot_operate");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
            } else {
                Component component = Component.translatableClient(EnumI18nType.MESSAGE, "past_day_cannot_operate");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
            }
        } else if (signStatusCode == EnumSignInStatus.REWARDED.getCode()) {
            Component component = Component.translatableClient(EnumI18nType.MESSAGE, "already_get_reward");
            NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
        } else {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                Component component = Component.literal(EnumSignInStatus.valueOf(signStatusCode).getDescription() + ": " + DateUtils.toString(cellDate));
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FFFF55));
            }
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        keyManager.refresh(mouseX, mouseY);
        // 绘制背景
        this.renderBackground(matrixStack);
        // 绘制缩放背景纹理
        this.renderBackgroundTexture(matrixStack);

        // 渲染年份
        double yearX = bgX + SakuraSignIn.getThemeTextureCoordinate().getYearCoordinate().getX() * this.scale;
        double yearY = bgY + SakuraSignIn.getThemeTextureCoordinate().getYearCoordinate().getY() * this.scale;
        String yearTitle = DateUtils.toLocalStringYear(SakuraSignIn.getCalendarCurrentDate(), Minecraft.getInstance().options.languageCode);
        super.font.draw(matrixStack, yearTitle, (float) yearX, (float) yearY, SakuraSignIn.getThemeTextureCoordinate().getTextColorDate());

        // 渲染月份
        double monthX = bgX + SakuraSignIn.getThemeTextureCoordinate().getMonthCoordinate().getX() * this.scale;
        double monthY = bgY + SakuraSignIn.getThemeTextureCoordinate().getMonthCoordinate().getY() * this.scale;
        String monthTitle = DateUtils.toLocalStringMonth(SakuraSignIn.getCalendarCurrentDate(), Minecraft.getInstance().options.languageCode);
        super.font.draw(matrixStack, monthTitle, (float) monthX, (float) monthY, SakuraSignIn.getThemeTextureCoordinate().getTextColorDate());

        // 渲染操作按钮
        for (Integer op : BUTTONS.keySet()) {
            OperationButton button = BUTTONS.get(op);
            TextureCoordinate textureCoordinate = SakuraSignIn.getThemeTextureCoordinate();
            switch (OperationButtonType.valueOf(op)) {
                case RIGHT_ARROW:
                    // 如果宽度和高度与月份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getMonthCoordinate().getWidth() && button.getHeight() == textureCoordinate.getMonthCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与月份相同，则将坐标设置为月份右边的位置
                    if (button.getX() == textureCoordinate.getMonthCoordinate().getX() && button.getY() == textureCoordinate.getMonthCoordinate().getY()) {
                        String localStringMonth = DateUtils.toLocalStringMonth(DateUtils.format(String.format("%d-12-01", DateUtils.getYearPart(SakuraSignIn.getCalendarCurrentDate()))), Minecraft.getInstance().options.languageCode);
                        button.setX((monthX - bgX + font.width(localStringMonth) + 1) / this.scale);
                    }
                    break;
                case DOWN_ARROW:
                    // 如果宽度和高度与年份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getYearCoordinate().getWidth() && button.getHeight() == textureCoordinate.getYearCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与年份相同，则将坐标设置为年份右边的位置
                    if (button.getX() == textureCoordinate.getYearCoordinate().getX() && button.getY() == textureCoordinate.getYearCoordinate().getY()) {
                        button.setX((yearX - bgX + font.width(yearTitle) + 1) / this.scale);
                    }
                    break;
                case LEFT_ARROW:
                    // 如果宽度和高度与月份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getMonthCoordinate().getWidth() && button.getHeight() == textureCoordinate.getMonthCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与月份相同，则将坐标设置为月份左边的位置
                    if (button.getX() == textureCoordinate.getMonthCoordinate().getX() && button.getY() == textureCoordinate.getMonthCoordinate().getY()) {
                        button.setX((monthX - bgX - 1) / this.scale - button.getWidth());
                    }
                    break;
                case UP_ARROW:
                    // 如果宽度和高度与年份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getYearCoordinate().getWidth() && button.getHeight() == textureCoordinate.getYearCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与年份相同，则将坐标设置为年份左边的位置
                    if (button.getX() == textureCoordinate.getYearCoordinate().getX() && button.getY() == textureCoordinate.getYearCoordinate().getY()) {
                        button.setX((yearX - bgX - 1) / this.scale - button.getWidth());
                    }
                    break;
                case THEME_ORIGINAL_BUTTON:
                case THEME_SAKURA_BUTTON:
                case THEME_CLOVER_BUTTON:
                case THEME_MAPLE_BUTTON:
                case THEME_CHAOS_BUTTON:
                    // 如选中主题为当前主题则设置为鼠标按下(选中)状态
                    if (SakuraSignIn.getThemeTexture().getPath().equalsIgnoreCase(OperationButtonType.valueOf(op).getPath())) {
                        button.setNormalV(button.getTapV());
                        button.setHoverV(button.getTapV());
                    } else {
                        button.setNormalV(textureCoordinate.getThemeUV().getV0());
                        button.setHoverV(textureCoordinate.getThemeHoverUV().getV0());
                    }
                    button.setNormalU((op - 100) * textureCoordinate.getThemeUV().getUWidth());
                    button.setHoverU((op - 100) * textureCoordinate.getThemeHoverUV().getUWidth());
                    button.setTapU((op - 100) * textureCoordinate.getThemeTapUV().getUWidth());
                    button.setX((op - 100) * (textureCoordinate.getThemeCoordinate().getWidth() + textureCoordinate.getThemeHMargin()) + textureCoordinate.getThemeCoordinate().getX());
                    break;
                case INFO:
                    button.setRotatedAngle(button.isHovered() ? 10 : 0);
                    break;
            }
            button.setBaseX(bgX);
            button.setBaseY(bgY);
            button.setScale(this.scale);
            button.render(matrixStack, keyManager);
        }

        // 渲染所有格子
        for (SignInCell cell : signInCells) {
            cell.render(matrixStack, super.font, keyManager);
        }

        if (!this.SIGN_IN_SCREEN_TIPS) {
            boolean showRewardDetail = true;
            if (Minecraft.getInstance().player != null) {
                showRewardDetail = Minecraft.getInstance().player.hasPermissions(ServerConfig.PERMISSION_REWARD_DETAIL.get());
            }
            if (showRewardDetail) {
                // 渲染格子弹出层
                for (SignInCell cell : signInCells) {
                    if (cell.isShowHover() && cell.isMouseOver(keyManager)) {
                        if (keyManager.onlyShiftPressed()) {
                            AbstractGuiUtils.drawPopupMessage(Text.translatable(EnumI18nType.TIPS, "how_to_sign_in").setMatrixStack(matrixStack).setFont(this.font).setAlign(Text.Align.CENTER), mouseX, mouseY, super.width, super.height);
                        } else {
                            cell.renderTooltip(matrixStack, super.font, this.itemRenderer, keyManager);
                        }
                    }
                }
            }

            // 绘制弹出选项
            popupOption.render(matrixStack, keyManager);

            // 渲染操作按钮的弹出提示
            for (Integer op : BUTTONS.keySet()) {
                OperationButton button = BUTTONS.get(op);
                if (op == INFO.getCode()) {
                    if (Minecraft.getInstance().player != null) {
                        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(Minecraft.getInstance().player);
                        button.setTooltip(
                                Text.translatable(EnumI18nType.TIPS, "sign_in_info"
                                                , signInData.getSignInCard()
                                                , signInData.getContinuousSignInDays()
                                                , signInData.getTotalSignInDays())
                                        .setMatrixStack(matrixStack)
                                        .setFont(this.font)
                        );
                    }
                }
                button.renderPopup(matrixStack, keyManager);
            }
        }
        // 显示开屏提示
        else {
            AbstractGuiUtils.fill(matrixStack, 4, 4, super.width - 8, super.height - 8, 0xDD000000, 15);
            float x, y;
            tips.setMatrixStack(matrixStack).setFont(super.font);
            int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
            int textWidth = AbstractGuiUtils.multilineTextWidth(tips);
            int buttonWidth = Math.min(100, textWidth / 2 - 5);
            x = (super.width - textWidth) / 2.0f;
            y = (super.height - (textHeight + 4 + 20)) / 2.0f;
            AbstractGuiUtils.drawString(tips, x, y);
            super.buttons.stream().filter(button -> button instanceof Button
                    && (button.getMessage().getString().equalsIgnoreCase(Text.translatable(EnumI18nType.OPTION, "confirm").getContent()))
                    || (button.getMessage().getString().equalsIgnoreCase(Text.translatable(EnumI18nType.OPTION, "no_remind").getContent()))).forEach(button -> {
                if (button.getMessage().getString().equalsIgnoreCase(Text.translatable(EnumI18nType.OPTION, "confirm").getContent())) {
                    button.x = (int) x;
                } else {
                    button.x = (int) x + textWidth - buttonWidth;
                }
                button.y = (int) y + textHeight + 4;
                button.setWidth(buttonWidth);
                button.setHeight(20);
                button.render(matrixStack, mouseX, mouseY, partialTicks);
            });
        }
    }

    /**
     * 检测鼠标点击事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        keyManager.mouseClicked(button, mouseX, mouseY);
        // 清空弹出选项
        if (!popupOption.isHovered()) {
            popupOption.clear();
            BUTTONS.forEach((key, value) -> {
                if (value.isHovered()) {
                    value.setPressed(true);
                }
            });
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        keyManager.refresh(mouseX, mouseY);
        AtomicBoolean updateLayout = new AtomicBoolean(false);
        AtomicBoolean updateRewardCell = new AtomicBoolean(false);
        AtomicBoolean updateTextureAndCoordinate = new AtomicBoolean(false);
        AtomicBoolean flag = new AtomicBoolean(false);
        if (popupOption.isHovered()) {
            LOGGER.debug("选择了弹出选项:\tIndex: {}\tContent: {}", popupOption.getSelectedIndex(), popupOption.getSelectedString());
            if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "open_theme_folder").equals(popupOption.getSelectedString())
                    || I18nUtils.getTranslationClient(EnumI18nType.OPTION, "theme_folder_empty").equals(popupOption.getSelectedString())
            ) {
                SakuraUtils.openFileInFolder(new File(SakuraUtils.getConfigPath().toFile(), "themes").toPath());
            } else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "select_theme_file").equals(popupOption.getSelectedString())) {
                // TODO
            } else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "edit_theme_file").equals(popupOption.getSelectedString())) {
                // TODO
            } else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "create_theme_file").equals(popupOption.getSelectedString())) {
                Theme theme = new Theme(true);
                ThemeEditScreen themeScreen = new ThemeEditScreen(theme);
                StringInputScreen.Args args = new StringInputScreen.Args()
                        .setParentScreen(themeScreen)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("name")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_name").setShadow(true))
                                .setValidator((input) -> {
                                    if (StringUtils.isNullOrEmptyEx(input.getValue())) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                    }
                                    return null;
                                })
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("author")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_author_name").setShadow(true))
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("version")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
                                .setDefaultValue("1.0")
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("description")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_description").setShadow(true))
                                .setAllowEmpty(true)
                        )
                        .setCallback(input -> {
                            theme.setName(input.getValue("name"))
                                    .setAuthor(input.getValue("author"))
                                    .setVersion(input.getValue("version"))
                                    .setDescription(input.getValue("description"));
                            File dir = SakuraUtils.getThemePath()
                                    .resolve("edit")
                                    .resolve(FileUtils.replacePathChar(theme.getName())).toFile();
                            if (!dir.exists()) dir.mkdirs();
                            theme.setConfigFile(new File(dir, "config" + SakuraSignIn.THEME_JSON_SUFFIX));
                            try (FileWriter writer = new FileWriter(theme.getConfigFile())) {
                                writer.write(JsonUtils.PRETTY_GSON.toJson(theme.toJson()));
                            } catch (IOException e) {
                                LOGGER.error("Error saving theme config: ", e);
                            }
                        });
                StringInputScreen inputScreen = new StringInputScreen(args);
                Minecraft.getInstance().setScreen(inputScreen);
            } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT && CollectionUtils.isNotNullOrEmpty(themeFileList)) {
                ClientPlayerEntity player = Minecraft.getInstance().player;
                String selectedFile = themeFileList.get(popupOption.getSelectedIndex() - 4).getPath();
                if (player != null) {
                    Component component = Component.translatableClient(EnumI18nType.MESSAGE, "selected_theme_file_s", selectedFile);
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component));
                    ResourceLocation resourceLocation = TextureUtils.loadCustomTexture(selectedFile);
                    if (TextureUtils.isTextureAvailable(resourceLocation)) {
                        ClientConfig.THEME.set(selectedFile);
                        updateTextureAndCoordinate.set(true);
                        updateLayout.set(true);
                    }
                }
            }
            popupOption.clear();
        } else {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            // 控制按钮
            BUTTONS.forEach((key, value) -> {
                if (value.isHovered() && value.isPressed()) {
                    this.handleOperation(value, updateLayout, updateRewardCell, updateTextureAndCoordinate, flag);
                }
                value.setPressed(false);
            });
            if (!flag.get()) {
                // 日历格子
                for (SignInCell cell : signInCells) {
                    if (cell.isShowIcon() && cell.isMouseOver(keyManager)) {
                        if (player != null) {
                            this.handleSignIn(button, cell, player);
                        }
                        flag.set(true);
                    }
                }

            }
        }
        if (updateTextureAndCoordinate.get()) this.updateTextureAndCoordinate();
        if (updateRewardCell.get()) this.updateRewardCell();
        if (updateLayout.get()) this.updateLayout();
        keyManager.mouseReleased(button, mouseX, mouseY);
        return flag.get() ? flag.get() : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyManager.mouseMoved(mouseX, mouseY);
        BUTTONS.forEach((key, value) -> value.setHovered(value.isMouseOverEx(mouseX, mouseY)));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        keyManager.mouseScrolled(delta, mouseX, mouseY);
        if (!popupOption.addScrollOffset(delta)) {
            // 奖励悬浮层
            for (SignInCell cell : signInCells) {
                if (cell.isShowIcon() && cell.isShowHover() && cell.isMouseOver(keyManager)) {
                    if (delta > 0) {
                        cell.setTooltipScrollOffset(Math.max(cell.getTooltipScrollOffset() - 1, 0));
                    } else if (delta < 0) {
                        cell.setTooltipScrollOffset(Math.min(cell.getTooltipScrollOffset() + 1, cell.getRewardCount() - SignInCell.TOOLTIP_MAX_VISIBLE_ITEMS));
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        keyManager.keyPressed(keyCode);
        boolean consumed = false;
        if (keyCode == GLFWKey.GLFW_KEY_ESCAPE
                || keyCode == ClientModEventHandler.SIGN_IN_SCREEN_KEY.getKey().getValue()
                || keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()
        ) {
            if (this.SIGN_IN_SCREEN_TIPS) this.SIGN_IN_SCREEN_TIPS = false;
            else if (this.previousScreen != null) Minecraft.getInstance().setScreen(this.previousScreen);
            else this.onClose();
            consumed = true;
        }
        return consumed || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean consumed = false;
        if (ClientConfig.KEY_SIGN_LAST_MONTH.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), -1));
            this.updateRewardCell();
            this.updateLayout();
            consumed = true;
        } else if (ClientConfig.KEY_SIGN_NEXT_MONTH.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), 1));
            this.updateRewardCell();
            this.updateLayout();
            consumed = true;
        } else if (ClientConfig.KEY_SIGN_LAST_YEAR.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), -1));
            this.updateRewardCell();
            this.updateLayout();
            consumed = true;
        } else if (ClientConfig.KEY_SIGN_NEXT_YEAR.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), 1));
            this.updateRewardCell();
            this.updateLayout();
            consumed = true;
        }
        keyManager.keyReleased(keyCode);
        return consumed || super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
