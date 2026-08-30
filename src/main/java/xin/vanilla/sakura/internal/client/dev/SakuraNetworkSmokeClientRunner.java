package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Calendar;
import java.util.Date;

/** 通过真实客户端网络包覆盖签到、补签、奖励同步与重启后的摘要恢复。 */
public final class SakuraNetworkSmokeClientRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TIMEOUT_TICKS = 1_000;
    private static final int SETTLE_TICKS = 440;
    private static State state = State.CONNECT;
    private static int ticks;
    private static boolean signInRequested;
    private static boolean reSignInRequested;

    private SakuraNetworkSmokeClientRunner() {
    }

    public static void tick() {
        if (!SakuraNetworkSmokeStatus.enabled() || state == State.FINISHED) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (++ticks > TIMEOUT_TICKS && state != State.SERVER_SETTLE) {
                throw new IllegalStateException("Timed out in " + state);
            }
            switch (state) {
                case CONNECT:
                    connect(client);
                    break;
                case LOGIN_SYNC:
                    waitForLogin(client);
                    break;
                case SIGN_IN:
                    signIn(client);
                    break;
                case RE_SIGN_IN:
                    reSignIn(client);
                    break;
                case SERVER_SETTLE:
                    if (ticks >= SETTLE_TICKS) finish(client);
                    break;
                default:
                    break;
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private static void connect(Minecraft client) {
        String host = System.getProperty("sakura.networkSmoke.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("sakura.networkSmoke.port", "25578"));
        ServerData server = new ServerData("Sakura Network Smoke", host + ':' + port, false);
        client.setScreen(new ConnectingScreen(client.screen, client, server));
        state = State.LOGIN_SYNC;
        ticks = 0;
    }

    private static void waitForLogin(Minecraft client) {
        if (client.player == null || !SakuraClientState.isEnabled()) return;
        SakuraNetworkSmokeStatus.append("PASS remote-login-sync");
        if ("phase-two".equals(SakuraNetworkSmokeStatus.phase())) {
            IPlayerSignInData data = SakuraPlayerData.get(client.player);
            if (data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
                throw new IllegalStateException("Persisted summary mismatch: days="
                        + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
            }
            SakuraNetworkSmokeStatus.append("PASS persisted-player-data-client");
            state = State.SERVER_SETTLE;
            ticks = 0;
            return;
        }
        state = State.SIGN_IN;
        ticks = 0;
    }

    private static void signIn(Minecraft client) {
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        if (data.getTotalSignInDays() == 0 && !signInRequested) {
            SakuraNetwork.sendToServer(new SignInPacket(DateUtils.toDateTimeString(new Date()),
                    true, ESignInType.SIGN_IN));
            signInRequested = true;
            return;
        }
        if (data.getTotalSignInDays() == 0) return;
        if (data.getTotalSignInDays() != 1) {
            throw new IllegalStateException("Unexpected sign-in count " + data.getTotalSignInDays());
        }
        SakuraNetworkSmokeStatus.append("PASS sign-in-client");
        state = State.RE_SIGN_IN;
        ticks = 0;
    }

    private static void reSignIn(Minecraft client) {
        IPlayerSignInData data = SakuraPlayerData.get(client.player);
        if (data.getTotalSignInDays() == 1 && !reSignInRequested) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            SakuraNetwork.sendToServer(new SignInPacket(DateUtils.toDateTimeString(calendar.getTime()),
                    true, ESignInType.RE_SIGN_IN));
            reSignInRequested = true;
            return;
        }
        if (data.getTotalSignInDays() == 1) return;
        if (data.getTotalSignInDays() != 2 || data.getSignInCard() != 0) {
            throw new IllegalStateException("Unexpected re-sign-in summary: days="
                    + data.getTotalSignInDays() + ", cards=" + data.getSignInCard());
        }
        SakuraNetworkSmokeStatus.append("PASS re-sign-in-client");
        state = State.SERVER_SETTLE;
        ticks = 0;
    }

    private static void finish(Minecraft client) {
        state = State.FINISHED;
        SakuraNetworkSmokeStatus.append("FINISHED " + SakuraNetworkSmokeStatus.phase());
        LOGGER.info("Sakura network smoke client finished {}", SakuraNetworkSmokeStatus.phase());
        client.stop();
    }

    private static void fail(Minecraft client, String message) {
        state = State.FINISHED;
        SakuraNetworkSmokeStatus.append("FAIL client " + message);
        LOGGER.error("Sakura network smoke client failed: {}", message);
        client.stop();
    }

    private enum State {
        CONNECT, LOGIN_SYNC, SIGN_IN, RE_SIGN_IN, SERVER_SETTLE, FINISHED
    }
}
