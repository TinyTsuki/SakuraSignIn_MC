package xin.vanilla.sakura.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.network.packet.AdvancementPacket;
import xin.vanilla.sakura.network.packet.PlayerDataReceivedNotice;
import xin.vanilla.sakura.network.packet.PlayerDataSyncPacket;

public class ClientProxy {
    public static final Logger LOGGER = LogManager.getLogger();

    public static void handleSynPlayerData(PlayerDataSyncPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                player.setData(PlayerDataAttachment.PLAYER_DATA, packet.getData());
                PacketDistributor.SERVER.noArg().send(new PlayerDataReceivedNotice());
                LOGGER.debug("Client: Player data received successfully.");
            } catch (Exception ignored) {
                LOGGER.debug("Client: Player data received failed.");
            }
            SakuraSignIn.setEnabled(true);
        }
    }

    public static void handleAdvancement(AdvancementPacket packet) {
        SakuraSignIn.setAdvancementData(packet.getAdvancements());
    }
}
