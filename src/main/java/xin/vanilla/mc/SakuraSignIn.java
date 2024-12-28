package xin.vanilla.mc;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmlserverevents.FMLServerStartingEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.command.SignInCommand;
import xin.vanilla.mc.config.ClientConfig;
import xin.vanilla.mc.config.RewardOptionDataManager;
import xin.vanilla.mc.config.ServerConfig;
import xin.vanilla.mc.event.ClientEventHandler;
import xin.vanilla.mc.network.AdvancementData;
import xin.vanilla.mc.network.ModNetworkHandler;
import xin.vanilla.mc.network.SplitPacket;
import xin.vanilla.mc.rewards.RewardList;
import xin.vanilla.mc.screen.coordinate.TextureCoordinate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(SakuraSignIn.MODID)
public class SakuraSignIn {

    public static final String MODID = "sakura_sign_in";
    public static final String PNG_CHUNK_NAME = "vacb";

    public static final Logger LOGGER = LogManager.getLogger();

    /**
     * 是否有对应的服务端
     */
    @Getter
    @Setter
    private static boolean enabled;
    /**
     * 奖励配置页面侧边栏是否开启
     */
    @Getter
    @Setter
    private static boolean rewardOptionBarOpened = false;
    /**
     * 签到页面当前显示的日期
     */
    @Getter
    @Setter
    private static Date calendarCurrentDate;

    /**
     * 背景材质
     */
    @Getter
    @Setter
    private static ResourceLocation themeTexture = null;
    /**
     * 背景材质坐标
     */
    @Setter
    public static TextureCoordinate themeTextureCoordinate = null;
    /**
     * 是否使用内置主题特殊图标
     */
    @Getter
    @Setter
    private static boolean specialVersionTheme = false;
    /**
     * 奖励配置数据
     */
    @Getter
    @Setter
    private static List<AdvancementData> advancementData;
    /**
     * 玩家权限等级
     */
    @Getter
    @Setter
    private static int permissionLevel;

    /**
     * 分片网络包缓存
     */
    @Getter
    private static final Map<String, List<? extends SplitPacket>> packetCache = new ConcurrentHashMap<>();

    /**
     * 玩家能力同步状态
     */
    @Getter
    private static final Map<String, Boolean> playerCapabilityStatus = new ConcurrentHashMap<>();

    @Getter
    private static final RewardList clipboard = new RewardList();

    public SakuraSignIn() {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到MinecraftForge的事件总线，以便监听和处理游戏内的各种事件
        MinecraftForge.EVENT_BUS.register(this);

        // 注册服务器和客户端配置
        // MinecraftForge.EVENT_BUS.addListener(this::onLoadConfig);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);

        // 注册客户端设置事件到MOD事件总线
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    // 服务器启动时加载数据
    private void onServerStarting(FMLServerStartingEvent event) {
        RewardOptionDataManager.loadRewardOption();
        LOGGER.debug("SignIn data loaded.");
    }

    // 服务器关闭时保存数据
    private void onServerStopping(FMLServerStoppingEvent event) {
        // RewardOptionDataManager.saveRewardOption();
    }

    public static TextureCoordinate getThemeTextureCoordinate(boolean nonNull) {
        if (nonNull && (themeTextureCoordinate == null || themeTexture == null)) ClientEventHandler.loadThemeTexture();
        return themeTextureCoordinate;
    }

    @NonNull
    public static TextureCoordinate getThemeTextureCoordinate() {
        return getThemeTextureCoordinate(true);
    }

    /**
     * 在客户端设置阶段触发的事件处理方法
     * 此方法主要用于接收 FML 客户端设置事件，并执行相应的初始化操作
     */
    @SubscribeEvent
    public void onClientSetup(final FMLClientSetupEvent event) {
        // 注册键绑定
        LOGGER.debug("Registering key bindings");
        ClientEventHandler.registerKeyBindings();
        // 创建配置文件目录
        ClientEventHandler.createConfigPath();
    }

    /**
     * 注册命令事件的处理方法
     * 当注册命令事件被触发时，此方法将被调用
     * 该方法主要用于注册签到命令到事件调度器
     *
     * @param event 注册命令事件对象，通过该对象可以获取到事件调度器
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        // 注册签到命令到事件调度器
        SignInCommand.register(event.getDispatcher());
    }

    /**
     * 玩家注销事件
     *
     * @param event 玩家注销事件对象，通过该对象可以获取到注销的玩家对象
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGGER.debug("Player has logged out.");
        // 获取退出的玩家对象
        Player player = event.getPlayer();
        // 判断是否在客户端并且退出的玩家是客户端的当前玩家
        if (player.getCommandSenderWorld().isClientSide) {
            if (Minecraft.getInstance().player.getUUID().equals(player.getUUID())) {
                LOGGER.debug("Current player has logged out.");
                // 当前客户端玩家与退出的玩家相同
                enabled = false;
            }
        }
    }

    /**
     * 打开指定路径的文件夹
     */
    @OnlyIn(Dist.CLIENT)
    public static void openFileInFolder(Path path) {
        try {
            if (Files.isDirectory(path)) {
                // 如果是文件夹，直接打开文件夹
                openFolder(path);
            } else if (Files.isRegularFile(path)) {
                // 如果是文件，打开文件所在的文件夹，并选中文件
                openFolderAndSelectFile(path);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open file/folder: ", e);
        }
    }

    private static void openFolder(Path path) {
        try {
            // Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", path.toString()).start();
            }
            // macOS
            else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                new ProcessBuilder("open", path.toString()).start();
            }
            // Linux
            else {
                new ProcessBuilder("xdg-open", path.toString()).start();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to open folder: ", e);
        }
    }

    private static void openFolderAndSelectFile(Path file) {
        try {
            // Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", file.toString()).start();
            }
            // macOS
            else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                new ProcessBuilder("open", "-R", file.toString()).start();
            }
            // Linux
            else {
                new ProcessBuilder("xdg-open", "--select", file.toString()).start();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to open folder and select file: ", e);
        }
    }
}
