package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.TagParser;
import xin.vanilla.banira.client.data.NotificationLogEntry;
import xin.vanilla.banira.client.util.NotificationManager;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.network.SakuraNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Observes real network-delivered notifications and monthly records, without injecting client data. */
final class SakuraNetworkSmokeDeliveryCheck {
    private final long startedAt = System.currentTimeMillis();
    private final Set<Long> seen = new HashSet<>();
    private final List<Component> details = new ArrayList<>();
    private int pages;
    private int pressureDay;
    private boolean requested;
    private boolean notificationsVerified;

    void collect() {
        if (!"phase-one".equals(SakuraNetworkSmokeStatus.phase())) return;
        List<NotificationLogEntry> pending = new ArrayList<>();
        for (NotificationLogEntry entry : NotificationManager.get().getLog()) {
            if (!seen.add(entry.id())) break;
            if (entry.timestamp() >= startedAt && "network".equals(entry.source())) pending.add(entry);
        }
        Collections.reverse(pending);
        for (NotificationLogEntry entry : pending) {
            Component page = entry.component();
            if (!"word.sakura_sign_in.receive_reward_success".equals(page.text())) continue;
            require("SUCCESS".equals(entry.styleName()), "Reward result lost success style");
            require("sakura_sign_in.reward".equals(entry.notificationType())
                    || "sakura_sign_in.sign_in".equals(entry.notificationType()), "Reward result lost notification type");
            pages++;
            // Initial C2S sign-in and make-up use a separate three-entry fixture.
            if (pages <= 2) continue;
            for (Component detail : page.getChildren()) {
                if (", ".equals(detail.text()) && detail.getChildren().isEmpty()) continue;
                require((detail.color().rgb() & 0xFFFFFF) == 0x00FF00,
                        "Successful reward detail lost its explicit color");
                details.add(detail);
            }
        }
    }

    boolean verify(Minecraft client, Properties checkpoint) throws Exception {
        collect();
        int cycles = Integer.parseInt(checkpoint.getProperty("cycles"));
        int rewards = Integer.parseInt(checkpoint.getProperty("rewardsPerClaim"));
        if ("phase-one".equals(SakuraNetworkSmokeStatus.phase()) && !notificationsVerified) {
            int expected = cycles * 2 * rewards;
            if (details.size() < expected) return false;
            require(details.size() == expected, "Duplicate reward detail pages: " + details.size());
            String language = checkpoint.getProperty("notificationLanguage");
            for (int index = 0; index < expected; index++) {
                com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
                require(parser.parse(checkpoint.getProperty("notificationDetail." + (index / rewards) + "." + (index % rewards)))
                                .equals(details.get(index).toJson()),
                        "Reward detail missing, changed, or reordered at " + index);
            }
            if (rewards == 256) require(pages > cycles * 2 + 2, "Stress notifications were not batched");
            SakuraNetworkSmokeStatus.append("PASS reward-notification-details-client pages=" + (pages - 2)
                    + " details=" + details.size() + " language=" + language + " explicit-colors=preserved");
            notificationsVerified = true;
        }
        while (pressureDay < cycles * 2) {
            Date date = new Date(Long.parseLong(checkpoint.getProperty("pressureDay." + pressureDay)));
            if (!requested) {
                SakuraNetwork.requestMonth(date);
                requested = true;
                return false;
            }
            SignInRecord actual = SakuraPlayerData.get(client.player).getSignInRecords().stream()
                    .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == DateUtils.toDateInt(date))
                    .findFirst().orElse(null);
            if (actual == null || !actual.isRewarded()) return false;
            require(TagParser.parseTag(checkpoint.getProperty("pressureRecord." + pressureDay))
                    .equals(actual.writeToNBT()), "Monthly reward detail mismatch at pressure day " + pressureDay);
            pressureDay++;
            requested = false;
        }
        SakuraNetworkSmokeStatus.append("PASS all-pressure-month-details-client days=" + pressureDay
                + " rewards-per-day=" + rewards);
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
