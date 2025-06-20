package xin.vanilla.sakura.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.network.packet.AdvancementToClient;
import xin.vanilla.sakura.network.packet.PlayerDataReceivedToServer;
import xin.vanilla.sakura.network.packet.PlayerDataSyncToBoth;
import xin.vanilla.sakura.network.packet.RewardCellRequestToServer;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.util.SakuraUtils;

public class ClientProxy {
    public static final Logger LOGGER = LogManager.getLogger();

    public static void handleSynPlayerData(PlayerDataSyncToBoth packet) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                IPlayerSignInData clientData = PlayerSignInDataCapability.getData(player);
                PlayerSignInDataCapability.PLAYER_DATA.readNBT(clientData, null, packet.getData().serializeNBT());
                SakuraUtils.sendPacketToServer(new PlayerDataReceivedToServer());
                LOGGER.debug("Client: Player data received successfully.");
            } catch (Exception ignored) {
                LOGGER.debug("Client: Player data received failed.");
            }
            SakuraSignIn.setEnabled(true);
        }
    }

    public static void handleAdvancement(AdvancementToClient packet) {
        SakuraSignIn.setAdvancementData(packet.getAdvancements());
    }

    public static void handleRewardCellDirtied() {
        if (Minecraft.getInstance().screen != null
                && Minecraft.getInstance().screen instanceof SignInScreen
        ) {
            SakuraUtils.sendPacketToServer(new RewardCellRequestToServer(SakuraSignIn.getCalendarCurrentDate()));
        }
    }
}
