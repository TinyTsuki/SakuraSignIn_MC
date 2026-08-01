package xin.vanilla.sakura.data.time;

import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.config.CommonConfig;

import java.util.Date;

/**
 * Sakura 的服务器与客户端校准时钟。
 */
public final class SakuraClock {
    private SakuraClock() {
    }

    public static Date serverNow() {
        return calibrate(new Date(),
                CommonConfig.get().dateTime().serverTime(),
                CommonConfig.get().dateTime().serverCalibrationTime());
    }

    public static Date clientNow() {
        return calibrate(new Date(),
                SakuraClientState.getClientServerTime().key(),
                SakuraClientState.getClientServerTime().value());
    }

    private static Date calibrate(Date current, String originalValue,
                                  String calibratedValue) {
        Date original = DateUtils.format(originalValue);
        Date calibrated = DateUtils.format(calibratedValue);
        if (original == null || calibrated == null || original.equals(calibrated)) {
            return current;
        }
        return DateUtils.addDate(current, DateUtils.dateOfTwo(original, calibrated));
    }
}
