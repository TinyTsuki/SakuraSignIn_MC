package xin.vanilla.sakura;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.command.SignInCommand;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.CustomConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.RewardCell;
import xin.vanilla.sakura.event.ClientModEventHandler;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.network.packet.SplitPacket;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(SakuraSignIn.MODID)
public class SakuraSignIn {
    public static final Logger LOGGER = LogManager.getLogger();

    public final static String DEFAULT_COMMAND_PREFIX = "sakura";

    public static final String MODID = "sakura_sign_in";
    public static final String ARTIFACT_ID = "xin.vanilla";
    public static final String PNG_CHUNK_NAME = "vacb";
    public static final String THEME_FILE_SUFFIX = ".sakura.png";
    public static final String THEME_JSON_SUFFIX = ".sakura.json";
    public static final String THEME_EDITING_SUFFIX = ".sakura.texture";


    // region 服务端全局变量

    /**
     * 服务端实例
     */
    @Getter
    private static MinecraftServer serverInstance;

    /**
     * 已安装mod的玩家列表</br>
     * 玩家UUID:是否已同步数据</br>
     * 在该map的玩家都为已安装mod</br>
     * 布尔值为false时为未同步数据，将会在玩家tick事件中检测并同步数据
     */
    @Getter
    private static final Map<String, Boolean> playerDataStatus = new ConcurrentHashMap<>();

    /**
     * 玩家语言缓存</br>
     * 玩家UUID -> 语言
     */
    @Getter
    private static final Map<String, String> playerLanguageCache = new ConcurrentHashMap<>();

    // endregion 服务端全局变量


    // region 客户端全局变量

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
    @Getter
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
     * 分片网络包缓存
     */
    @Getter
    private static final Map<String, List<? extends SplitPacket>> packetCache = new ConcurrentHashMap<>();

    /**
     * 客户端-服务器时间
     */
    @Getter
    private static final KeyValue<String, String> clientServerTime = new KeyValue<String, String>()
            .setKey(DateUtils.toDateTimeString(new Date(0)))
            .setValue(DateUtils.toString(new Date(0)));

    /**
     * 签到页面数据
     */
    @Getter
    private static final Map<String, RewardCell> rewardCellMap = new ConcurrentHashMap<>();

    // endregion 客户端全局变量

    public SakuraSignIn() {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册服务器启动关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);

        // 注册客户端设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        // 注册公共设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    /**
     * 客户端设置阶段事件
     */
    @SubscribeEvent
    public void onClientSetup(final FMLClientSetupEvent event) {
        // 注册键绑定
        ClientModEventHandler.registerKeyBindings();
        // 创建主题文件目录
        SakuraUtils.createThemePath();
        // 加载主题纹理
        SakuraUtils.loadThemeTexture();
    }

    /**
     * 公共设置阶段事件
     */
    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        CustomConfig.loadCustomConfig(false);
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        serverInstance = event.getServer();
        RewardConfigManager.loadRewardOption(true);
        LOGGER.debug("SignIn data loaded.");
    }

    /**
     * 命令注册事件
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        SignInCommand.register(event.getDispatcher());
    }


    // region 资源ID

    public static ResourceLocation emptyResource() {
        return createResource("", "");
    }

    public static ResourceLocation createResource(String path) {
        return createResource(SakuraSignIn.MODID, path);
    }

    public static ResourceLocation createResource(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parseResource(String location) {
        return ResourceLocation.tryParse(location);
    }

    // endregion 资源ID


    // region 外部方法
    @SuppressWarnings("unused")
    public void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }
    // endregion 外部方法

}
