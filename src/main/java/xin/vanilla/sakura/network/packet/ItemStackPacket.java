package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;

import java.io.IOException;

/**
 * ItemStackPacket类用于在网络中传输ItemStack数据
 * 它提供了将ItemStack序列化和反序列化的方法，以便于网络传输
 */
@Getter
public class ItemStackPacket implements CustomPacketPayload {
    public final static Type<ItemStackPacket> TYPE = new Type<>(new ResourceLocation(SakuraSignIn.MODID, "item_stack"));
    public final static StreamCodec<ByteBuf, ItemStackPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ItemStackPacket decode(@NotNull ByteBuf byteBuf) {
            return new ItemStackPacket((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ItemStackPacket packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    private static final Logger LOGGER = LogManager.getLogger();

    // 存储要传输的ItemStack对象
    private final ItemStack itemStack;

    /**
     * 构造函数，用于创建一个包含指定ItemStack的ItemStackPacket
     *
     * @param itemStack 要传输的ItemStack对象
     */
    public ItemStackPacket(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    /**
     * 构造函数，用于从FriendlyByteBuf中读取数据并重构ItemStackPacket
     *
     * @param buf 包含ItemStack数据的FriendlyByteBuf
     */
    public ItemStackPacket(FriendlyByteBuf buf) {
        this.itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
    }

    /**
     * 将ItemStack数据写入FriendlyByteBuf，准备进行网络传输
     *
     * @param buf 用于存储ItemStack数据的FriendlyByteBuf
     */
    public void toBytes(@NonNull FriendlyByteBuf buf) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, itemStack);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理物品堆数据包的函数
     * 该函数用于处理来自客户端的物品堆数据包，决定物品是否添加到玩家的库存中，或者以物品实体的形式生成在世界上
     *
     * @param packet 包含物品堆信息的数据包
     * @param ctx    用于访问网络事件上下文的供应商函数，包括访问玩家信息和设置数据包处理状态
     */
    public static void handle(ItemStackPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.enqueueWork(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player() instanceof ServerPlayer player) {
                    // 尝试将物品堆添加到玩家的库存中
                    boolean added = player.getInventory().add(packet.itemStack);
                    // 如果物品堆无法添加到库存，则以物品实体的形式生成在世界上
                    if (!added) {
                        ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), packet.itemStack);
                        try (Level level = player.level()) {
                            level.addFreshEntity(itemEntity);
                        } catch (IOException e) {
                            LOGGER.error("Failed to add item entity to world", e);
                        }
                    }
                }
            });
        }
    }
}
