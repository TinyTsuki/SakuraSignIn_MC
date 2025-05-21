package xin.vanilla.sakura;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.sakura.config.*;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.event.ClientModEventHandler;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.network.packet.SplitPacket;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod(SakuraSignIn.MODID)
public class SakuraSignIn {

    public final static String DEFAULT_COMMAND_PREFIX = "sakura";

    public static final String MODID = "sakura_sign_in";
    public static final String PNG_CHUNK_NAME = "vacb";

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 服务端实例
     */
    @Getter
    private static MinecraftServer serverInstance;

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

    /**
     * 客户端-服务器时间
     */
    @Getter
    private static final KeyValue<String, String> clientServerTime = new KeyValue<>(DateUtils.toDateTimeString(new Date(0)), DateUtils.toString(new Date(0)));

    public SakuraSignIn(IEventBus modEventBus, ModContainer modContainer) {

        // 注册网络通道
        modEventBus.addListener(ModNetworkHandler::registerPackets);
        // 注册服务器启动和关闭事件
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册服务器和客户端配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);
        // 注册数据附件
        PlayerDataAttachment.ATTACHMENT_TYPES.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ClientModEventHandler::onClientTick);
        }

        // 注册当前实例到MinecraftForge的事件总线，以便监听和处理游戏内的各种事件
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        RewardConfigManager.loadRewardOption();
        LOGGER.debug("SignIn data loaded.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 服务器关闭时保存数据
        // RewardOptionDataManager.saveRewardOption();
    }

    public static TextureCoordinate getThemeTextureCoordinate(boolean nonNull) {
        if (nonNull && (themeTextureCoordinate == null || themeTexture == null))
            ClientModEventHandler.loadThemeTexture();
        return themeTextureCoordinate;
    }

    @NonNull
    public static TextureCoordinate getThemeTextureCoordinate() {
        return getThemeTextureCoordinate(true);
    }

    /**
     * 注册命令事件的处理方法
     * 当注册命令事件被触发时，此方法将被调用
     * 该方法主要用于注册传送命令到事件调度器
     *
     * @param event 注册命令事件对象，通过该对象可以获取到事件调度器
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var commandDispatcher = event.getDispatcher();
        // 注册传送命令到事件调度器
        LOGGER.debug("Registering commands");
        SignInCommand.register(commandDispatcher);
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
        Player player = event.getEntity();
        // 判断是否在客户端并且退出的玩家是客户端的当前玩家
        if (player.getCommandSenderWorld().isClientSide) {
            LocalPlayer clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null && clientPlayer.getUUID().equals(player.getUUID())) {
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
