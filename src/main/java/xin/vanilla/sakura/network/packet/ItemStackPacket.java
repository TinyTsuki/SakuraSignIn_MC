package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * ItemStackPacket类用于在网络中传输ItemStack数据
 * 它提供了将ItemStack序列化和反序列化的方法，以便于网络传输
 */
@Getter
public class ItemStackPacket {
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
     * 构造函数，用于从PacketBuffer中读取数据并重构ItemStackPacket
     *
     * @param buf 包含ItemStack数据的PacketBuffer
     */
    public ItemStackPacket(PacketBuffer buf) {
        this.itemStack = buf.readItem();
    }

    /**
     * 将ItemStack数据写入PacketBuffer，准备进行网络传输
     *
     * @param buf 用于存储ItemStack数据的PacketBuffer
     */
    public void toBytes(PacketBuffer buf) {
        buf.writeItemStack(itemStack, false);
    }

    /**
     * 处理物品堆数据包的函数
     * 该函数用于处理来自客户端的物品堆数据包，决定物品是否添加到玩家的库存中，或者以物品实体的形式生成在世界上
     *
     * @param packet 包含物品堆信息的数据包
     * @param ctx    用于访问网络事件上下文的供应商函数，包括访问玩家信息和设置数据包处理状态
     */
    public static void handle(ItemStackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.get().enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                // 尝试将物品堆添加到玩家的库存中
                boolean added = player.inventory.add(packet.itemStack);
                // 如果物品堆无法添加到库存，则以物品实体的形式生成在世界上
                if (!added) {
                    ItemEntity itemEntity = new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), packet.itemStack);
                    player.level.addFreshEntity(itemEntity);
                }
            }
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.get().setPacketHandled(true);
    }
}
