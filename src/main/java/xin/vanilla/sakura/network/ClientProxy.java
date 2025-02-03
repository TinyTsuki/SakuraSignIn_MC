package xin.vanilla.sakura.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.capability.IPlayerSignInData;
import xin.vanilla.sakura.capability.PlayerSignInDataCapability;

public class ClientProxy {
    public static final Logger LOGGER = LogManager.getLogger();

    public static void handleSynPlayerData(PlayerDataSyncPacket packet) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                IPlayerSignInData clientData = PlayerSignInDataCapability.getData(player);
                PlayerSignInDataCapability.PLAYER_DATA.readNBT(clientData, null, packet.getData().serializeNBT());
                ModNetworkHandler.INSTANCE.sendToServer(new PlayerDataReceivedNotice());
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
