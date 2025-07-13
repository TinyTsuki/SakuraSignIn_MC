package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.enums.*;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.screen.component.OperationButton;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.theme.*;
import xin.vanilla.sakura.util.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ThemeEditScreen extends SakuraScreen {

    private static final Logger LOGGER = LogManager.getLogger();

    public ThemeEditScreen(Theme theme) {
        super(Component.translatable(EnumI18nType.TITLE, "theme_edit_title"));
        this.theme = theme;
    }

    // region 变量定义

    /**
     * 主题实例
     */
    private final Theme theme;

    /**
     * 菜单按钮
     */
    private final OperationButton menuButton = new OperationButton(0, context -> {
        AbstractGuiUtils.fill(context.matrixStack
                , (int) context.button.getRealX()
                , (int) context.button.getRealY()
                , (int) context.button.getRealWidth()
                , (int) context.button.getRealHeight()
                , 0xFFFFFFFF, 3
        );

        int x1 = (int) context.button.getRealX(), y1 = (int) context.button.getRealY();
        int[] dxTable = {0, 1, 2, 3, 4, 3, 2, 1};
        int[] dyTable = {2, 1, 0, 1, 2, 3, 4, 3};
        int sector;
        if (context.button.isMouseOver(context.keyManager.getMouseX(), context.keyManager.getMouseY())) {
            sector = (int) ((super.getRenderCount() / 20) % 8);
        } else {
            double angle = Math.atan2(y1 - context.keyManager.getMouseY(), x1 - context.keyManager.getMouseX());
            if (angle < 0) angle += 2 * Math.PI;
            sector = (int) ((angle / (2 * Math.PI)) * 8 + 0.5) % 8;
        }
        int drawX = x1 + dxTable[sector];
        int drawY = y1 + dyTable[sector];
        AbstractGuiUtils.fill(context.matrixStack, drawX, drawY, 5, 5, 0xFF000000, 3);
    }).setX(-99).setY(-99).setWidth(9).setHeight(9);

    /**
     * 当前渲染帧
     */
    private int renderIndex = 0;
    /**
     * 当前组件ID
     */
    private long currentComponentId = 0;

    // endregion 变量定义


    // region 对象定义


    // endregion 对象定义


    // region 私有方法

    private void handleMenuClick(MouseReleasedHandleArgs args) {
        if (menuButton.isPressed() || keyManager.isKeyPressed(GLFWKey.GLFW_KEY_MENU)) {
            if (keyManager.isMouseRightPressed() || keyManager.isKeyPressed(GLFWKey.GLFW_KEY_MENU)) {
                popupOption.clear();
                popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "add_theme_component"));
                popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "edit_theme_info"));
                if (theme.isMinecraftBackground()) {
                    popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "disable_mc_bg"));
                } else {
                    popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "enable_mc_bg"));
                }
                popupOption.build(super.font, args.getMouseX(), args.getMouseY(), "menu");

            }
            args.setConsumed(true);
        }
    }

    @Override
    public void handlePopupOption(MouseReleasedHandleArgs args) {
        LOGGER.debug("选择了弹出选项:\tButton: {}\tId: {}\tIndex: {}\tContent: {}", args.getButton(), popupOption.getId(), popupOption.getSelectedIndex(), popupOption.getSelectedString());
        String selectedString = popupOption.getSelectedString();
        // 启用原版背景渲染
        if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "enable_mc_bg").equals(selectedString)) {
            theme.setMinecraftBackground(true);
        }
        // 禁用原版背景渲染
        else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "disable_mc_bg").equals(selectedString)) {
            theme.setMinecraftBackground(false);
        }
        // 编辑主题信息
        else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "edit_theme_info").equals(selectedString)) {
            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setName("name")
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_name").setShadow(true))
                            .setDefaultValue(theme.getName())
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
                            .setDefaultValue(theme.getAuthor())
                    )
                    .addWidget(new StringInputScreen.Widget()
                            .setName("version")
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
                            .setDefaultValue(theme.getVersion())
                    )
                    .addWidget(new StringInputScreen.Widget()
                            .setName("description")
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_description").setShadow(true))
                            .setDefaultValue(theme.getDescription())
                            .setAllowEmpty(true)
                    )
                    .setCallback(input -> theme.setName(input.getValue("name"))
                            .setAuthor(input.getValue("author"))
                            .setVersion(input.getValue("version"))
                            .setDescription(input.getValue("description"))
                    );
            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
        }
        // 添加组件
        else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "add_theme_component").equals(selectedString)) {
            popupOption.clear();
            for (EnumThemeComponentType type : EnumThemeComponentType.getWithType(0, 1)) {
                popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "theme_component_" + type.name().toLowerCase()));
            }
            popupOption.build(super.font, args.getMouseX(), args.getMouseY(), "add_theme_component");
            args.setClearPopup(false);
        }
        // 选择
        else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "theme_component_custom").equals(selectedString)) {
            // super.clearInput()
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_name").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_author_name").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_author_name").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_author_name").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_author_name").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_version").setShadow(true))
            //         )
            //         .addWidget(new Widget()
            //                 .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_description").setShadow(true))
            //         )
            //         .setCallback(input -> {
            //             LOGGER.debug(input.getFirstValue());
            //             LOGGER.debug(input.getLastValue());
            //         });
            // super.initInput();
        }
        // 选择背景文件
        else if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "theme_component_background").equals(selectedString)) {
            Consumer<StringInputScreen.Results> callback = input -> Minecraft.getInstance().execute(() -> {
                // TODO 临时删除，方便测试
                theme.getComponents().clear();

                String textureId = input.getValue("textureId");
                String filePath = input.getValue("file");
                try {
                    File selectedFile = new File(filePath);
                    File imgFile = new File(SakuraUtils.getThemePath("edit", FileUtils.replacePathChar(theme.getName())).toFile(), textureId + SakuraSignIn.THEME_EDITING_SUFFIX);

                    if (!imgFile.exists()) Files.copy(selectedFile.toPath(), imgFile.toPath());
                    if (!theme.getTextureCache().containsKey(textureId)) {
                        theme.getTextureCache().put(textureId, TextureUtils.loadCustomTexture(imgFile.getAbsolutePath()));
                    }

                    BufferedImage bufferedImage = PNGUtils.readImage(imgFile);
                    theme.getTextureMap().put(textureId, new Coordinate()
                            .setUWidth(bufferedImage.getWidth())
                            .setVHeight(bufferedImage.getHeight())
                            .setUvWidth(bufferedImage.getWidth())
                            .setUvHeight(bufferedImage.getHeight())
                    );
                    theme.getComponents()
                            .put(new ThemeComponent()
                                    .setType(EnumThemeComponentType.BACKGROUND)
                                    .setRenderList(new RenderInfoList(new RenderInfo()
                                            .setCoordinate(new Coordinate()
                                                    .setTextureId(textureId)
                                                    .setWType(EnumSizeType.RELATIVE_PERCENT)
                                                    .setWidth(1.0)
                                                    .setHType(EnumSizeType.RELATIVE_PERCENT)
                                                    .setHeight(1.0)
                                            )
                                            .setFillType(EnumThemeTextureFillType.TILE)
                                            .setScale(0.05)
                                            .setAlpha(0xAA)
                                    ))
                            )
                            .put(new ThemeComponent()
                                    .setType(EnumThemeComponentType.CUSTOM)
                                    .setRenderList(new RenderInfoList(new RenderInfo()
                                            .setCoordinate(new Coordinate().setX(60).setY(60))
                                            .setRotationAngle(45)
                                            .setScale(0.75)
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "cdk_expired").setColor(EnumMCColor.BLACK.getColor()).setBgColor(EnumMCColor.WHITE.getColor()))
                                    ))
                            )
                            .put(new ThemeComponent()
                                    .setType(EnumThemeComponentType.CUSTOM)
                                    .setRenderList(new RenderInfoList(new RenderInfo()
                                            .setCoordinate(new Coordinate().setX(110).setY(60))
                                            .setRotationAngle(145)
                                            .setScale(1.75)
                                            .setText(Component.literal("测试旋转145度").setColorArgb(0xAAAA0ABC))
                                    ))
                            )
                            .put(new ThemeComponent()
                                    .setType(EnumThemeComponentType.CUSTOM)
                                    .setRenderList(new RenderInfoList(new RenderInfo()
                                            .setCoordinate(new Coordinate().setX(250).setY(100))
                                            .setFlipHorizontal(true)
                                            .setScale(3)
                                            .setText(Component.literal("测试翻转").setColorArgb(0x9999AA00))
                                    ))
                            );
                } catch (Exception e) {
                    input.setRunningResult(Component.translatableClient(EnumI18nType.TIPS, "select_file_error", filePath)
                            .append("\n")
                            .append(e.toString())
                            .toString()
                    );
                }
            });
            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setName("textureId")
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_theme_texture_id").setShadow(true))
                            .setDisabled(true)
                            .setValidator((input) -> {
                                if (StringUtils.isNullOrEmptyEx(input.getValue())) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "theme_texture_id_error").toString();
                                }
                                return null;
                            })
                    )
                    .addWidget(new StringInputScreen.Widget()
                            .setName("file")
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_or_select_file").setShadow(true))
                            .setType(StringInputScreen.WidgetType.FILE)
                            .setFileFilter("*.png")
                            .setValidator((input) -> {
                                if (StringUtils.isNullOrEmptyEx(input.getValue())) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "select_file_empty", input.getValue()).toString();
                                } else if (!input.getValue().endsWith(".png")) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "select_file_error", input.getValue()).toString();
                                } else {
                                    File png = new File(input.getValue());
                                    if (!png.isFile() || !png.exists()) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "select_file_notfound", input.getValue()).toString();
                                    }
                                }
                                return null;
                            })
                            .setChanged((inputs) -> {
                                File file = new File(inputs.getValue().getValue());
                                String sha = FileUtils.computeFileHashOrElse(file, StringUtils.md5(file.getName()));
                                inputs.getValue("textureId").setValue(sha);
                            })
                    )
                    .setCallback(callback);
            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
        }
        menuButton.setPressed(false);
    }

    private void renderThemeComponent(MatrixStack matrixStack, float partialTicks) {
        for (ThemeComponent component : theme.getVisible()) {
            RenderInfo renderInfo = component.getRenderList(ThemeComponent.RenderType.NORMAL).get(renderIndex);
            Coordinate coordinateThree = ThemeUtils.buildCoordinateTree(theme.getVisible(), component.getId(), ThemeComponent.RenderType.NORMAL, renderIndex);
            if (coordinateThree == null) continue;
            Coordinate coordinate = ThemeUtils.getRealCoordinate(coordinateThree, this, keyManager);
            Coordinate clone = coordinate.clone().readUV(theme);
            // 计算渲染坐标
            switch (renderInfo.getFillType()) {
                case FILL: {
                    double parentW = clone.getWidth();
                    double parentH = clone.getHeight();
                    double w = clone.getUWidth();
                    double h = clone.getVHeight();

                    double scale = Math.max(parentW / w, parentH / h);

                    clone.setWidth(w * scale);
                    clone.setHeight(h * scale);
                    renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone), component.isSelected());
                }
                break;
                case FIT: {
                    double parentW = clone.getWidth();
                    double parentH = clone.getHeight();
                    double w = clone.getUWidth();
                    double h = clone.getVHeight();

                    double scale = Math.min(parentW / w, parentH / h);

                    clone.setWidth(w * scale);
                    clone.setHeight(h * scale);
                    renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone), component.isSelected());
                }
                break;
                case STRETCH: {
                    renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone), component.isSelected());
                }
                break;
                case TILE: {
                    if (renderInfo.getScale() < 1.0) {
                        double parentW = clone.getWidth();
                        double parentH = clone.getHeight();
                        double w = Math.max(3, (clone.getWidth() * renderInfo.getScale()));
                        double h = Math.max(3, clone.getVHeight() * (w / clone.getUWidth()));
                        int xCount = (int) Math.ceil(parentW / w);
                        int yCount = (int) Math.ceil(parentH / h);
                        for (int i = 0; i < xCount; i++) {
                            for (int j = 0; j < yCount; j++) {
                                Coordinate clone1 = clone.clone();
                                clone1.setWidth(w / renderInfo.getScale());
                                clone1.setHeight(h / renderInfo.getScale());
                                clone1.setX(clone1.getX() + i * w);
                                clone1.setY(clone1.getY() + j * h);
                                renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone1), component.isSelected());
                            }
                        }
                    } else {
                        renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone), component.isSelected());
                    }
                }
                break;
                case CENTER: {
                    double parentW = clone.getWidth();
                    double parentH = clone.getHeight();
                    double w = clone.getUWidth() * renderInfo.getScale();
                    double h = clone.getVHeight() * renderInfo.getScale();

                    clone.setWidth(w);
                    clone.setHeight(h);
                    clone.setX(clone.getX() + (parentW - w) / 2);
                    clone.setY(clone.getY() + (parentH - h) / 2);
                    renderComponent(matrixStack, renderInfo.clone().setCoordinate(clone), component.isSelected());
                }
                break;
            }
        }
    }

    private void renderComponent(MatrixStack matrixStack, RenderInfo renderInfo, boolean selected) {
        AbstractGuiUtils.TransformArgs args = new AbstractGuiUtils.TransformArgs(matrixStack)
                .setCoordinate(renderInfo.getCoordinate())
                .setScale(renderInfo.getScale())
                .setAngle(renderInfo.getRotationAngle())
                .setCenter(renderInfo.getRotationCenter())
                .setFlipHorizontal(renderInfo.isFlipHorizontal())
                .setFlipVertical(renderInfo.isFlipVertical())
                .setAlpha(renderInfo.getAlpha())
                .setBlend(true);
        // 绘制背景颜色
        if (renderInfo.getBgColor() != 0) {
            AbstractGuiUtils.renderByTransform(args
                    , (drawArgs) -> AbstractGuiUtils.fill(drawArgs.getStack()
                            , (int) drawArgs.getX()
                            , (int) drawArgs.getY()
                            , (int) drawArgs.getWidth()
                            , (int) drawArgs.getHeight()
                            , renderInfo.getBgColor()
                    )
            );
        }
        // 绘制纹理
        if (renderInfo.hasUVInfo()) {
            AbstractGuiUtils.renderByTransform(args
                    , drawArgs -> {
                        int uvWidth, uvHeight;
                        if (theme.isEditing()) {
                            AbstractGuiUtils.bindTexture(theme.getTextureCache().get(renderInfo.getCoordinate().getTextureId()));
                            uvWidth = theme.getTextureMap().get(renderInfo.getCoordinate().getTextureId()).getUvWidth();
                            uvHeight = theme.getTextureMap().get(renderInfo.getCoordinate().getTextureId()).getUvHeight();
                        } else {
                            AbstractGuiUtils.bindTexture(theme.getResourceLocation());
                            uvWidth = theme.getTotalWidth();
                            uvHeight = theme.getTotalHeight();
                        }
                        AbstractGuiUtils.blit(drawArgs.getStack()
                                , (int) drawArgs.getX()
                                , (int) drawArgs.getY()
                                , (int) drawArgs.getWidth()
                                , (int) drawArgs.getHeight()
                                , renderInfo.getCoordinate().getU0()
                                , renderInfo.getCoordinate().getV0()
                                , renderInfo.getCoordinate().getUWidth()
                                , renderInfo.getCoordinate().getVHeight()
                                , uvWidth
                                , uvHeight
                        );
                    }
            );
        }
        // 绘制文本
        if (renderInfo.hasTextInfo()) {
            Text text = new Text(renderInfo.getText())
                    .setFont(this.font);
            AbstractGuiUtils.renderByTransform(args
                            .setWidth(AbstractGuiUtils.multilineTextWidth(text))
                            .setHeight(AbstractGuiUtils.multilineTextHeight(text))
                    , (drawArgs) -> AbstractGuiUtils.drawMultilineText(
                            text.setMatrixStack(drawArgs.getStack())
                            , drawArgs.getX()
                            , drawArgs.getY()
                    )
            );
        }
        // 绘制前景颜色
        if (renderInfo.getFgColor() != 0) {
            AbstractGuiUtils.renderByTransform(args
                    , (drawArgs) -> AbstractGuiUtils.fill(drawArgs.getStack()
                            , (int) drawArgs.getX()
                            , (int) drawArgs.getY()
                            , (int) drawArgs.getWidth()
                            , (int) drawArgs.getHeight()
                            , renderInfo.getFgColor()
                    )
            );
        }
        if (selected)
            AbstractGuiUtils.renderByTransform(args
                    , (drawArgs) -> AbstractGuiUtils.fillOutLine(drawArgs.getStack()
                            , (int) drawArgs.getX() - 1
                            , (int) drawArgs.getY() - 1
                            , (int) drawArgs.getWidth() + 2
                            , (int) drawArgs.getHeight() + 2
                            , 1
                            , 0x88FFF13B)
            );
        // 绘制悬浮文本
        if (renderInfo.hasTooltipInfo()) {
            // TODO 判断是否被悬浮
            AbstractGuiUtils.drawPopupMessage(
                    new Text(renderInfo.getTooltip())
                            .setMatrixStack(matrixStack)
                            .setFont(this.font)
                    , renderInfo.getCoordinate().getX()
                    , renderInfo.getCoordinate().getY()
                    , super.width
                    , super.height
            );
        }
    }

    // endregion 私有方法


    @Override
    protected void init_() {
        if (this.menuButton.getX() == -99 && this.menuButton.getY() == -99) {
            this.menuButton.setX(5).setY((super.height - 9) / 2.0);
        } else {
            menuButton.setX(Math.min(super.width - menuButton.getRealWidth() / 2, Math.max(-menuButton.getRealWidth() / 2, menuButton.getRealX())))
                    .setY(Math.min(super.height - menuButton.getRealHeight() / 2, Math.max(-menuButton.getRealHeight() / 2, menuButton.getRealY())));
        }
        theme.getComponents().sorted();
        theme.getDarkComponents().sorted();
        if (theme.getFile() == null) {
            theme.setFile(SakuraUtils.getThemePath().resolve(FileUtils.replacePathChar(theme.getName()) + SakuraSignIn.THEME_FILE_SUFFIX).toFile());
            theme.getFile().mkdirs();
            try {
                PNGUtils.writeImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), theme.getFile());
                theme.setTotalWidth(1);
                theme.setTotalHeight(1);
            } catch (IOException ignored) {
            }
        }
        theme.setResourceLocation(TextureUtils.loadCustomTexture(theme.getFile().getAbsolutePath()));
    }

    @Override
    void updateLayout() {
    }

    @Override
    public void render_(MatrixStack matrixStack, float partialTicks) {
        // 绘制背景
        if (theme.isMinecraftBackground()) this.renderBackground(matrixStack);

        // 绘制主题组件
        this.renderThemeComponent(matrixStack, partialTicks);

        menuButton.render(matrixStack, keyManager);
    }


    // region 事件处理

    /**
     * 窗口关闭时
     */
    @Override
    public void removed_() {
    }

    /**
     * 检测鼠标点击事件
     */
    @Override
    public void mouseClicked_(MouseClickedHandleArgs args) {

        menuButton.setPressed(menuButton.isMouseOver(args.getMouseX(), args.getMouseY())
                && (keyManager.isMouseLeftPressed() || keyManager.isMouseRightPressed())
        );

    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public void mouseReleased_(MouseReleasedHandleArgs args) {

        if (!this.keyManager.isMouseMoved()) {
            this.handleMenuClick(args);
        }

        menuButton.setPressed(false);

    }

    /**
     * 鼠标移动事件
     */
    @Override
    public void mouseMoved_() {
        if (menuButton.isPressed()) {
            double realWidth = menuButton.getRealWidth();
            double realHeight = menuButton.getRealHeight();
            double x = keyManager.getMouseX() - realWidth / 2;
            double y = keyManager.getMouseY() - realHeight / 2;
            menuButton.setX(Math.min(super.width - realWidth / 2, Math.max(-realWidth / 2, x)))
                    .setY(Math.min(super.height - realHeight / 2, Math.max(-realHeight / 2, y)));
        }
    }

    /**
     * 鼠标滚动事件
     */
    @Override
    public void mouseScrolled_(MouseScoredHandleArgs args) {

    }

    /**
     * 键盘按下事件
     */
    @Override
    public void keyPressed_(KeyPressedHandleArgs args) {

        // 关闭弹出层选项
        if (keyManager.isKeyPressed(GLFWKey.GLFW_KEY_ESCAPE)) {
            if (!popupOption.isEmpty()) {
                popupOption.clear();
                args.setConsumed(true);
            }
        }

    }

    /**
     * 键盘松开事件
     */
    @Override
    public void keyReleased_(KeyReleasedHandleArgs args) {

        // 打开模拟点击菜单按钮
        if (keyManager.isKeyPressed(GLFWKey.GLFW_KEY_MENU)) {
            if (!popupOption.isEmpty()) {
                popupOption.clear();
                args.setConsumed(true);
            } else {
                MouseReleasedHandleArgs mouseArgs = new MouseReleasedHandleArgs()
                        .setMouseX(menuButton.getRealX() + menuButton.getRealWidth() / 2)
                        .setMouseY(menuButton.getRealY() + menuButton.getRealHeight() / 2);
                handleMenuClick(mouseArgs);
                args.setConsumed(mouseArgs.isConsumed());
            }
        }
        // 保存主题配置信息
        else if (ClientConfig.KEY_OPTION_SAVE.get().stream().anyMatch(keyManager::isKeyPressed)) {
            if (theme.getConfigFile() == null) {
                File configFile = SakuraUtils.getThemePath()
                        .resolve("edit")
                        .resolve(FileUtils.replacePathChar(theme.getName()))
                        .resolve("config" + SakuraSignIn.THEME_JSON_SUFFIX)
                        .toFile();
                configFile.getParentFile().mkdirs();
                theme.setConfigFile(configFile);
            }

            File configFile = theme.getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configFile.toPath()), StandardCharsets.UTF_8)) {
                writer.write(JsonUtils.PRETTY_GSON.toJson(theme.toJson()));
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(
                        Component.translatableClient(EnumI18nType.MESSAGE, "save_success")
                ));
            } catch (IOException e) {
                LOGGER.error("Error saving theme config to file {}:", configFile, e);
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(
                        Component.translatableClient(EnumI18nType.MESSAGE, "save_failed")
                ).setBgArgb(0x99FF5555));
            }

            args.setConsumed(true);
        }

    }

    @Override
    void onClose_() {
    }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // endregion 事件处理

}
