package xin.vanilla.sakura.util;

import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.CustomConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.enums.EnumCommandType;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRewardRule;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static xin.vanilla.sakura.SakuraSignIn.PNG_CHUNK_NAME;

public class SakuraUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Random random = new Random();

    // region 指令相关

    public static String getCommandPrefix() {
        String commandPrefix = CommonConfig.COMMAND_PREFIX.get();
        if (StringUtils.isNullOrEmptyEx(commandPrefix) || !commandPrefix.matches("^(\\w ?)+$")) {
            CommonConfig.COMMAND_PREFIX.set(SakuraSignIn.DEFAULT_COMMAND_PREFIX);
        }
        return CommonConfig.COMMAND_PREFIX.get().trim();
    }

    /**
     * 获取完整的指令
     */
    public static String getCommand(EnumCommandType type) {
        String prefix = SakuraUtils.getCommandPrefix();
        switch (type) {
            case HELP:
                return prefix + " help";
            case LANGUAGE:
                return prefix + " " + CommonConfig.COMMAND_LANGUAGE.get();
            case LANGUAGE_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_LANGUAGE.get() : "";
            case VIRTUAL_OP:
                return prefix + " " + CommonConfig.COMMAND_VIRTUAL_OP.get();
            case VIRTUAL_OP_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_VIRTUAL_OP.get() : "";
            case SIGN:
                return prefix + " " + CommonConfig.COMMAND_SIGN.get();
            case SIGN_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_SIGN.get() : "";
            case SIGNEX:
                return prefix + " " + CommonConfig.COMMAND_SIGNEX.get();
            case SIGNEX_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_SIGNEX.get() : "";
            case REWARD:
                return prefix + " " + CommonConfig.COMMAND_REWARD.get();
            case REWARD_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_REWARD.get() : "";
            case CDK:
                return prefix + " " + CommonConfig.COMMAND_CDK.get();
            case CDK_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_CDK.get() : "";
            case CARD:
            case CARD_GET:
            case CARD_SET:
                return prefix + " " + CommonConfig.COMMAND_CARD.get();
            case CARD_CONCISE:
            case CARD_GET_CONCISE:
            case CARD_SET_CONCISE:
                return isConciseEnabled(type) ? CommonConfig.COMMAND_CARD.get() : "";
            default:
                return "";
        }
    }

    /**
     * 获取指令权限等级
     */
    public static int getCommandPermissionLevel(EnumCommandType type) {
        switch (type) {
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return ServerConfig.PERMISSION_VIRTUAL_OP.get();
            case SIGN:
            case SIGN_CONCISE:
                return ServerConfig.PERMISSION_SIGN.get();
            case SIGNEX:
            case SIGNEX_CONCISE:
                return ServerConfig.PERMISSION_SIGNEX.get();
            case REWARD:
            case REWARD_CONCISE:
                return ServerConfig.PERMISSION_REWARD.get();
            case CDK:
            case CDK_CONCISE:
                return ServerConfig.PERMISSION_CDK.get();
            case CARD:
            case CARD_CONCISE:
                return ServerConfig.PERMISSION_CARD.get();
            case CARD_GET:
            case CARD_GET_CONCISE:
                return ServerConfig.PERMISSION_CARD_GET.get();
            case CARD_SET:
            case CARD_SET_CONCISE:
                return ServerConfig.PERMISSION_CARD_SET.get();
            default:
                return 0;
        }
    }

    /**
     * 判断指令是否启用简短模式
     */
    public static boolean isConciseEnabled(EnumCommandType type) {
        switch (type) {
            case LANGUAGE:
            case LANGUAGE_CONCISE:
                return CommonConfig.CONCISE_LANGUAGE.get();
            case VIRTUAL_OP:
            case VIRTUAL_OP_CONCISE:
                return CommonConfig.CONCISE_VIRTUAL_OP.get();
            case SIGN:
            case SIGN_CONCISE:
                return CommonConfig.CONCISE_SIGN.get();
            case SIGNEX:
            case SIGNEX_CONCISE:
                return CommonConfig.CONCISE_SIGNEX.get();
            case REWARD:
            case REWARD_CONCISE:
                return CommonConfig.CONCISE_REWARD.get();
            case CDK:
            case CDK_CONCISE:
                return CommonConfig.CONCISE_CDK.get();
            case CARD:
            case CARD_CONCISE:
            case CARD_GET:
            case CARD_GET_CONCISE:
            case CARD_SET:
            case CARD_SET_CONCISE:
                return CommonConfig.CONCISE_CARD.get();
            default:
                return false;
        }
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasCommandPermission(CommandSource source, EnumCommandType type) {
        return source.hasPermission(getCommandPermissionLevel(type)) || hasVirtualPermission(source.getEntity(), type);
    }

    /**
     * 判断是否拥有指令权限
     */
    public static boolean hasVirtualPermission(Entity source, EnumCommandType type) {
        // 若为玩家
        if (source instanceof PlayerEntity) {
            return VirtualPermissionManager.getVirtualPermission((PlayerEntity) source).stream()
                    .filter(Objects::nonNull)
                    .anyMatch(s -> s.replaceConcise() == type.replaceConcise());
        } else {
            return false;
        }
    }

    /**
     * 执行指令
     */
    public static boolean executeCommand(@NonNull ServerPlayerEntity player, @NonNull String command) {
        AtomicBoolean result = new AtomicBoolean(false);
        try {
            player.getServer().getCommands().performCommand(player.createCommandSourceStack()
                            .withCallback((source, success, r) -> result.set(success && r > 0))
                    , command
            );
        } catch (Exception e) {
            LOGGER.error("Failed to execute command: {}", command, e);
        }
        return result.get();
    }

    // endregion 指令相关

    // region 玩家与玩家背包

    /**
     * 获取随机玩家
     */
    public static ServerPlayerEntity getRandomPlayer() {
        try {
            List<ServerPlayerEntity> players = SakuraSignIn.getServerInstance().getPlayerList().getPlayers();
            return players.get(random.nextInt(players.size()));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 获取随机玩家UUID
     */
    public static UUID getRandomPlayerUUID() {
        PlayerEntity randomPlayer = getRandomPlayer();
        return randomPlayer != null ? randomPlayer.getUUID() : null;
    }

    /**
     * 通过UUID获取对应的玩家
     *
     * @param uuid 玩家UUID
     */
    public static ServerPlayerEntity getPlayer(UUID uuid) {
        try {
            return Minecraft.getInstance().level.getServer().getPlayerList().getPlayer(uuid);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 移除玩家背包中的指定物品
     *
     * @param player       玩家
     * @param itemToRemove 要移除的物品
     * @return 是否全部移除成功
     */
    public static boolean removeItemFromPlayerInventory(ServerPlayerEntity player, ItemStack itemToRemove) {
        IInventory inventory = player.inventory;

        // 剩余要移除的数量
        int remainingAmount = itemToRemove.getCount();
        // 记录成功移除的物品数量，以便失败时进行回滚
        int successfullyRemoved = 0;

        // 遍历玩家背包的所有插槽
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            // 获取背包中的物品
            ItemStack stack = inventory.getItem(i);
            ItemStack copy = itemToRemove.copy();
            copy.setCount(stack.getCount());

            // 如果插槽中的物品是目标物品
            if (stack.equals(copy, false)) {
                // 获取当前物品堆叠的数量
                int stackSize = stack.getCount();

                // 如果堆叠数量大于或等于剩余需要移除的数量
                if (stackSize >= remainingAmount) {
                    // 移除指定数量的物品
                    stack.shrink(remainingAmount);
                    // 记录成功移除的数量
                    successfullyRemoved += remainingAmount;
                    // 移除完毕
                    remainingAmount = 0;
                    break;
                } else {
                    // 移除该堆所有物品
                    stack.setCount(0);
                    // 记录成功移除的数量
                    successfullyRemoved += stackSize;
                    // 减少剩余需要移除的数量
                    remainingAmount -= stackSize;
                }
            }
        }

        // 如果没有成功移除所有物品，撤销已移除的部分
        if (remainingAmount > 0) {
            // 创建副本并还回成功移除的物品
            ItemStack copy = itemToRemove.copy();
            copy.setCount(successfullyRemoved);
            // 将已移除的物品添加回背包
            player.inventory.add(copy);
        }

        // 是否成功移除所有物品
        return remainingAmount == 0;
    }

    public static List<ItemStack> getPlayerItemList(ServerPlayerEntity player) {
        List<ItemStack> result = new ArrayList<>();
        if (player != null) {
            result.addAll(player.inventory.items);
            result.addAll(player.inventory.armor);
            result.addAll(player.inventory.offhand);
            result = result.stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() != Items.AIR).collect(Collectors.toList());
        }
        return result;
    }

    // endregion 玩家与玩家背包

    // region 消息相关

    /**
     * 广播消息
     *
     * @param player  发送者
     * @param message 消息
     */
    public static void broadcastMessage(ServerPlayerEntity player, Component message) {
        player.server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", player.getDisplayName(), message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 广播消息
     *
     * @param server  发送者
     * @param message 消息
     */
    public static void broadcastMessage(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", "Server", message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(SakuraUtils.getPlayerLanguage(player)), player.getUUID());
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ClientPlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(SakuraUtils.getClientLanguage()), player.getUUID());
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(Component.literal(message).toChatComponent(), player.getUUID());
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Component.translatable(key, args).setLanguageCode(SakuraUtils.getPlayerLanguage(player)).toChatComponent(), player.getUUID());
    }

    /**
     * 发送操作栏消息至所有玩家
     */
    public static void sendActionBarMessageToAll(Component message) {
        for (ServerPlayerEntity player : SakuraSignIn.getServerInstance().getPlayerList().getPlayers()) {
            sendActionBarMessage(player, message);
        }
    }

    /**
     * 发送操作栏消息
     */
    public static void sendActionBarMessage(ServerPlayerEntity player, Component message) {
        player.connection.send(new SChatPacket(message.toChatComponent(SakuraUtils.getPlayerLanguage(player)), ChatType.GAME_INFO, player.getUUID()));
    }

    /**
     * 广播数据包至所有玩家
     *
     * @param packet 数据包
     */
    public static void broadcastPacket(IPacket<?> packet) {
        SakuraSignIn.getServerInstance().getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
    }

    /**
     * 发送数据包至服务器
     */
    public static <MSG> void sendPacketToServer(MSG msg) {
        ModNetworkHandler.INSTANCE.sendToServer(msg);
    }

    /**
     * 发送数据包至玩家
     */
    public static <MSG> void sendPacketToPlayer(MSG msg, ServerPlayerEntity player) {
        ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    // endregion 消息相关

    // region 权限相关

    public static int getRewardPermissionLevel(EnumRewardRule rule) {
        int result = 0;
        switch (rule) {
            case BASE_REWARD:
                result = ServerConfig.PERMISSION_BASE_REWARD.get();
                break;
            case CONTINUOUS_REWARD:
                result = ServerConfig.PERMISSION_CONTINUOUS_REWARD.get();
                break;
            case CYCLE_REWARD:
                result = ServerConfig.PERMISSION_CYCLE_REWARD.get();
                break;
            case YEAR_REWARD:
                result = ServerConfig.PERMISSION_YEAR_REWARD.get();
                break;
            case MONTH_REWARD:
                result = ServerConfig.PERMISSION_MONTH_REWARD.get();
                break;
            case WEEK_REWARD:
                result = ServerConfig.PERMISSION_WEEK_REWARD.get();
                break;
            case DATE_TIME_REWARD:
                result = ServerConfig.PERMISSION_DATE_TIME_REWARD.get();
                break;
            case CUMULATIVE_REWARD:
                result = ServerConfig.PERMISSION_CUMULATIVE_REWARD.get();
                break;
            case RANDOM_REWARD:
                result = ServerConfig.PERMISSION_RANDOM_REWARD.get();
                break;
            case CDK_REWARD:
                result = ServerConfig.PERMISSION_CDK_REWARD.get();
                break;
        }
        return result;
    }

    // endregion 权限相关

    // region 杂项

    public static String getPlayerLanguage(PlayerEntity player) {
        try {
            return SakuraUtils.getValidLanguage(player, CustomConfig.getPlayerLanguage(getPlayerUUIDString(player)));
        } catch (IllegalArgumentException i) {
            return ServerConfig.DEFAULT_LANGUAGE.get();
        }
    }

    public static String getValidLanguage(@Nullable PlayerEntity player, @Nullable String language) {
        String result;
        if (StringUtils.isNullOrEmptyEx(language) || "client".equalsIgnoreCase(language)) {
            if (player instanceof ServerPlayerEntity) {
                result = SakuraUtils.getServerPlayerLanguage((ServerPlayerEntity) player);
            } else {
                result = SakuraUtils.getClientLanguage();
            }
        } else if ("server".equalsIgnoreCase(language)) {
            result = ServerConfig.DEFAULT_LANGUAGE.get();
        } else {
            result = language;
        }
        return result;
    }

    public static String getServerPlayerLanguage(ServerPlayerEntity player) {
        return player.getLanguage();
    }

    /**
     * 复制玩家语言设置
     *
     * @param originalPlayer 原始玩家
     * @param targetPlayer   目标玩家
     */
    public static void cloneServerPlayerLanguage(ServerPlayerEntity originalPlayer, ServerPlayerEntity targetPlayer) {
        FieldUtils.setPrivateFieldValue(ServerPlayerEntity.class, targetPlayer, FieldUtils.getPlayerLanguageFieldName(originalPlayer), getServerPlayerLanguage(originalPlayer));
    }

    public static String getClientLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected().getCode();
    }

    public static String getPlayerUUIDString(@NonNull PlayerEntity player) {
        return player.getUUID().toString();
    }

    /**
     * 获取玩家当前位置的环境亮度
     *
     * @param player 当前玩家实体
     * @return 当前环境亮度（范围0-15）
     */
    public static int getEnvironmentBrightness(PlayerEntity player) {
        int result = 0;
        if (player != null) {
            World world = player.level;
            BlockPos pos = player.blockPosition();
            // 获取基础的天空光亮度和方块光亮度
            int skyLight = world.getBrightness(LightType.SKY, pos);
            int blockLight = world.getBrightness(LightType.BLOCK, pos);
            // 获取世界时间、天气和维度的影响
            boolean isDay = world.isDay();
            boolean isRaining = world.isRaining();
            boolean isThundering = world.isThundering();
            boolean isUnderground = !world.canSeeSky(pos);
            // 判断世界维度（地表、下界、末地）
            if (world.dimension() == World.OVERWORLD) {
                // 如果在地表
                if (!isUnderground) {
                    if (isDay) {
                        // 白天地表：最高亮度
                        result = isThundering ? 6 : isRaining ? 9 : 15;
                    } else {
                        // 夜晚地表
                        // 获取月相，0表示满月，4表示新月
                        int moonPhase = world.getMoonPhase();
                        result = getMoonBrightness(moonPhase, isThundering, isRaining);
                    }
                } else {
                    // 地下环境
                    // 没有光源时最黑，有光源则受距离影响
                    result = Math.max(Math.min(blockLight, 12), 0);
                }
            } else if (world.dimension() == World.NETHER) {
                // 下界亮度较暗，但部分地方有熔岩光源
                // 近光源则亮度提升，但不会超过10
                result = Math.min(7 + blockLight / 2, 10);
            } else if (world.dimension() == World.END) {
                // 末地亮度通常较暗
                // 即使贴近光源，末地的亮度上限设为10
                result = Math.min(6 + blockLight / 2, 10);
            } else {
                result = Math.max(skyLight, blockLight);
            }
        }
        // 其他维度或者无法判断的情况，返回环境和方块光的综合值
        return result;
    }

    /**
     * 根据月相、天气等条件获取夜间月光亮度
     *
     * @param moonPhase    月相（0到7，0为满月，4为新月）
     * @param isThundering 是否雷暴
     * @param isRaining    是否下雨
     * @return 夜间月光亮度
     */
    private static int getMoonBrightness(int moonPhase, boolean isThundering, boolean isRaining) {
        if (moonPhase == 0) {
            // 满月
            return isThundering ? 3 : isRaining ? 5 : 9;
        } else if (moonPhase == 4) {
            // 新月（最暗）
            return isThundering ? 1 : 2;
        } else {
            // 其他月相，亮度随月相变化逐渐减小
            int moonLight = 9 - moonPhase;
            return isThundering ? Math.max(moonLight - 3, 1) : isRaining ? Math.max(moonLight - 2, 1) : moonLight;
        }
    }

    public static String getRewardRuleI18nKeyName(EnumRewardRule rule) {
        String result = "";
        switch (rule) {
            case BASE_REWARD:
                result = "reward_base";
                break;
            case CONTINUOUS_REWARD:
                result = "reward_continuous";
                break;
            case CYCLE_REWARD:
                result = "reward_cycle";
                break;
            case YEAR_REWARD:
                result = "reward_year";
                break;
            case MONTH_REWARD:
                result = "reward_month";
                break;
            case WEEK_REWARD:
                result = "reward_week";
                break;
            case DATE_TIME_REWARD:
                result = "reward_time";
                break;
            case CUMULATIVE_REWARD:
                result = "reward_cumulative";
                break;
            case RANDOM_REWARD:
                result = "reward_random";
                break;
            case CDK_REWARD:
                result = "reward_cdk";
                break;
        }
        return result;
    }

    /**
     * 获取配置文件目录
     */
    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID);
    }

    /**
     * 获取主题路径
     */
    public static Path getThemePath() {
        return getConfigPath().resolve("themes");
    }

    /**
     * 创建主题文件目录
     */
    public static void createThemePath() {
        File file = SakuraUtils.getThemePath().toFile();
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 加载主题纹理
     */
    @OnlyIn(Dist.CLIENT)
    public static void loadThemeTexture() {
        try {
            SakuraSignIn.setThemeTexture(TextureUtils.loadCustomTexture(ClientConfig.THEME.get()));
            SakuraSignIn.setSpecialVersionTheme(Boolean.TRUE.equals(ClientConfig.SPECIAL_THEME.get()));
            InputStream inputStream = Minecraft.getInstance().getResourceManager().getResource(SakuraSignIn.getThemeTexture()).getInputStream();
            SakuraSignIn.setThemeTextureCoordinate(PNGUtils.readLastPrivateChunk(inputStream, PNG_CHUNK_NAME));
        } catch (IOException | ClassNotFoundException ignored) {
        }
        if (SakuraSignIn.getThemeTexture() == null || SakuraSignIn.getThemeTextureCoordinate() == null) {
            // 使用默认配置
            SakuraSignIn.setThemeTextureCoordinate(TextureCoordinate.getDefault());
        }
        // 设置内置主题特殊图标UV的偏移量
        if (SakuraSignIn.isSpecialVersionTheme() && SakuraSignIn.getThemeTextureCoordinate().isSpecial()) {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(320);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(320);
        } else {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(0);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(0);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraSignIn.isEnabled()) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.getClientDate());
            Minecraft.getInstance().setScreen(new SignInScreen().setPreviousScreen(previousScreen));
        } else {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                Component component = Component.translatableClient(EnumI18nType.MESSAGE, "sakura_is_offline");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FFFF55));
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

    @OnlyIn(Dist.CLIENT)
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

    @OnlyIn(Dist.CLIENT)
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

    @OnlyIn(Dist.CLIENT)
    public static String chooseFileString(String desc, String... extensions) {
        if (StringUtils.isNullOrEmptyEx(desc)) desc = "Choose a file";

        String result;
        if (extensions.length > 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(extensions.length);
                Arrays.stream(extensions).forEach(s -> filter.put(stack.UTF8(s)));
                filter.flip();
                result = TinyFileDialogs.tinyfd_openFileDialog(desc, SakuraUtils.getThemePath().toAbsolutePath().toString(), filter, desc, false);
            }
        } else {
            result = TinyFileDialogs.tinyfd_openFileDialog(desc, SakuraUtils.getThemePath().toAbsolutePath().toString(), null, desc, false);
        }
        return result;
    }

    @OnlyIn(Dist.CLIENT)
    public static File chooseFile(String desc, String... extensions) {
        String result = chooseFileString(desc, extensions);
        return result == null ? null : new File(result);
    }

    @OnlyIn(Dist.CLIENT)
    public static String chooseRgbHex(String title) {
        if (StringUtils.isNullOrEmptyEx(title)) title = "Choose a color";
        return TinyFileDialogs.tinyfd_colorChooser(title, "#FFFFFF", null, BufferUtils.createByteBuffer(3));
    }

    public enum NotifyPopupIconType {
        info,
        warning,
        error,
    }

    @OnlyIn(Dist.CLIENT)
    public static int popupNotify(String title, String msg, NotifyPopupIconType iconType) {
        return TinyFileDialogs.tinyfd_notifyPopup(title, msg, iconType.name());
    }

    @OnlyIn(Dist.CLIENT)
    public static String getServerIP() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "";
    }

    // endregion 杂项
}
