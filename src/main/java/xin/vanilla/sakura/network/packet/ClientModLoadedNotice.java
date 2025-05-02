package xin.vanilla.sakura.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.data.PlayerDataAttachment;

public class ClientModLoadedNotice implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "client_mod_loaded");

    public ClientModLoadedNotice() {
    }

    public ClientModLoadedNotice(FriendlyByteBuf buf) {
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(ClientModLoadedNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.workHandler().execute(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player().isPresent()) {
                    ServerPlayer player = (ServerPlayer) ctx.player().get();
                    SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
                    // 同步玩家签到数据到客户端
                    PlayerDataAttachment.syncPlayerData(player);
                    // 同步签到奖励配置到客户端
                    for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardConfigManager.toSyncPacket(player).split()) {
                        player.connection.send(rewardOptionSyncPacket);
                    }
                    // 同步进度列表到客户端
                    for (AdvancementPacket advancementPacket : new AdvancementPacket((player).server.getAdvancements().getAllAdvancements()).split()) {
                        player.connection.send(advancementPacket);
                    }
                }
            });
        }
    }
}
