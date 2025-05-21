package xin.vanilla.sakura.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.PlayerSignInDataCapability;
import xin.vanilla.sakura.enums.ERewardRule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class SakuraUtils {


    public static String getCommandPrefix() {
        String commandPrefix = CommonConfig.COMMAND_PREFIX.get();
        if (StringUtils.isNullOrEmptyEx(commandPrefix) || !commandPrefix.matches("^(\\w ?)+$")) {
            CommonConfig.COMMAND_PREFIX.set(SakuraSignIn.DEFAULT_COMMAND_PREFIX);
        }
        return CommonConfig.COMMAND_PREFIX.get().trim();
    }

    // region 玩家与玩家背包

    /**
     * 获取随机玩家
     */
    public static ServerPlayerEntity getRandomPlayer() {
        try {
            List<ServerPlayerEntity> players = SakuraSignIn.getServerInstance().getPlayerList().getPlayers();
            return players.get(new Random().nextInt(players.size()));
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
        player.server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", player.getDisplayName(), message.toChatComponent()), true);
    }

    /**
     * 广播消息
     *
     * @param server  发送者
     * @param message 消息
     */
    public static void broadcastMessage(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", "Server", message.toChatComponent()), true);
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(SakuraUtils.getPlayerLanguage(player)));
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ClientPlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(SakuraUtils.getClientLanguage()));
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(Component.literal(message).toChatComponent());
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Component.translatable(key, args).setLanguageCode(SakuraUtils.getPlayerLanguage(player)).toChatComponent());
    }

    // endregion 消息相关

    // region 权限相关

    public static int getRewardPermissionLevel(ERewardRule rule) {
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

    public static String getPlayerLanguage(ServerPlayerEntity player) {
        return PlayerSignInDataCapability.getData(player).getValidLanguage(player);
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
        Object value = FieldUtils.getPrivateFieldValue(ServerPlayerEntity.class, player, FieldUtils.getPlayerLanguageFieldName(player));
        if (value == null) {
            return "en_us";
        }
        return (String) value;
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

    /**
     * 获取当前mod支持的mc版本
     *
     * @return 主版本*1000000+次版本*1000+修订版本， 如 1.16.5 -> 1 * 1000000 + 16 * 1000 + 5 = 10016005
     */
    public static int getMcVersion() {
        int version = 0;
        ModContainer container = ModList.get().getModContainerById(SakuraSignIn.MODID).orElse(null);
        if (container != null) {
            IModInfo.ModVersion minecraftVersion = container.getModInfo().getDependencies().stream()
                    .filter(dependency -> dependency.getModId().equalsIgnoreCase("minecraft"))
                    .findFirst()
                    .orElse(null);
            if (minecraftVersion != null) {
                ArtifactVersion lowerBound = minecraftVersion.getVersionRange().getRestrictions().get(0).getLowerBound();
                int majorVersion = lowerBound.getMajorVersion();
                int minorVersion = lowerBound.getMinorVersion();
                int incrementalVersion = lowerBound.getIncrementalVersion();
                version = majorVersion * 1000000 + minorVersion * 1000 + incrementalVersion;
            }
        }
        return version;
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
            BlockPos pos = player.getCommandSenderBlockPosition();
            // 获取基础的天空光亮度和方块光亮度
            int skyLight = world.getBrightness(LightType.SKY, pos);
            int blockLight = world.getBrightness(LightType.BLOCK, pos);
            // 获取世界时间、天气和维度的影响
            boolean isDay = world.isDay();
            boolean isRaining = world.isRaining();
            boolean isThundering = world.isThundering();
            boolean isUnderground = !world.canSeeSky(pos);
            // 判断世界维度（地表、下界、末地）
            if (world.dimension.getType() == DimensionType.OVERWORLD) {
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
            } else if (world.dimension.getType() == DimensionType.NETHER) {
                // 下界亮度较暗，但部分地方有熔岩光源
                // 近光源则亮度提升，但不会超过10
                result = Math.min(7 + blockLight / 2, 10);
            } else if (world.dimension.getType() == DimensionType.THE_END) {
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

    public static String getRewardRuleI18nKeyName(ERewardRule rule) {
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

    // endregion 杂项
}
