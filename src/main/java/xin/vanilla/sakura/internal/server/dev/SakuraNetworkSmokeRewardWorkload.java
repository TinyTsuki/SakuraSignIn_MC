package xin.vanilla.sakura.internal.server.dev;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.internal.dev.SakuraNetworkSmokeStatus;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real reward entry points against a bounded long-history player fixture. */
final class SakuraNetworkSmokeRewardWorkload {
    static final int CYCLES = 20;
    static final int REWARDS_PER_CLAIM = Integer.getInteger("sakura.networkSmoke.rewardsPerClaim", 48);
    private final LocalDate firstDay;
    private final Map<String, SakuraNetworkSmokeTimings> timings = new LinkedHashMap<>();
    private int cycles;
    private Date finalSignedDay;
    private Date finalMakeUpDay;

    SakuraNetworkSmokeRewardWorkload(Date now) {
        require(REWARDS_PER_CLAIM >= 3 && REWARDS_PER_CLAIM <= 512, "Invalid bounded reward count");
        firstDay = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(3);
        for (String name : new String[] {"sign-in", "claim-rewards", "duplicate-claim",
                "re-sign-in-rewards", "command-reward", "save-and-sync"}) {
            timings.put(name, new SakuraNetworkSmokeTimings(CYCLES));
        }
    }

    void prepare(ServerPlayer player) {
        RewardConfigManager.setRewardConfig(createRewardConfig());
        SakuraPlayerData.get(player).setSignInCard(CYCLES);
        SakuraPlayerData.saveAndSync(player);
        SakuraNetwork.sendSplitToPlayer(RewardConfigManager.toSyncPacket(player), player);
    }

    static RewardConfig createRewardConfig() {
        return createRewardConfig(null);
    }

    static RewardConfig createRewardConfig(Date day) {
        RewardConfig rewards = new RewardConfig();
        rewards.getBaseRewards().add(new Reward(identifiedItem(Items.APPLE, 5, day, 0), SakuraRewardTypes.ITEM));
        rewards.getBaseRewards().add(commandReward());
        for (int index = 2; index < REWARDS_PER_CLAIM; index++) {
            // Keep entries distinct through the normal merger; probability sampling is disabled by this fixture.
            rewards.getBaseRewards().add(new Reward(identifiedItem(Items.CARROT, 1, day, index), SakuraRewardTypes.ITEM,
                    java.math.BigDecimal.valueOf(index, 3)));
        }
        return rewards;
    }

    private static ItemStack identifiedItem(Item item, int count, Date day, int index) {
        ItemStack stack = new ItemStack(item, count);
        if (day != null) stack.setHoverName(new net.minecraft.network.chat.TextComponent(
                "smoke-" + DateUtils.toDateInt(day) + "-" + (index % 12)));
        return stack;
    }

    void runCycle(ServerPlayer player) {
        if (complete()) return;
        IPlayerSignInData data = SakuraPlayerData.get(player);
        // Clock changes and inventory cleanup are fixture work, outside operation timers.
        player.inventory.clearContent();
        LocalDate day = firstDay.plusDays(cycles * 3L);
        Date now = new Date();
        finalSignedDay = Date.from(day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
        finalMakeUpDay = Date.from(day.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
        CommonConfig.get().dateTime().serverTime(DateUtils.toDateTimeString(now))
                .serverCalibrationTime(DateUtils.toDateTimeString(finalSignedDay));
        int before = data.getTotalSignInDays();
        RewardConfigManager.setRewardConfig(createRewardConfig(finalSignedDay));

        measure("sign-in", () -> RewardManager.signIn(player, packet(finalSignedDay, false, ESignInType.SIGN_IN)));
        require(data.getTotalSignInDays() == before + 1 && data.isSignedOn(finalSignedDay)
                && !data.isRewardedOn(finalSignedDay), "Sign-in did not create an unclaimed day");
        require(count(player, Items.APPLE) == 0, "Sign-in unexpectedly granted a deferred reward");
        require(data.getSignInRecords().get(data.getSignInRecords().size() - 1).getRewardList().size()
                == REWARDS_PER_CLAIM, "Bulk rewards were collapsed before the measured claim");

        measure("claim-rewards", () -> RewardManager.signIn(player, packet(finalSignedDay, true, ESignInType.REWARD)));
        require(data.isRewardedOn(finalSignedDay), "Claim did not mark permanent reward state");
        assertInventory(player, 5, 1, REWARDS_PER_CLAIM - 2);

        measure("duplicate-claim", () -> RewardManager.signIn(player, packet(finalSignedDay, true, ESignInType.REWARD)));
        assertInventory(player, 5, 1, REWARDS_PER_CLAIM - 2);

        RewardConfigManager.setRewardConfig(createRewardConfig(finalMakeUpDay));
        measure("re-sign-in-rewards", () -> RewardManager.signIn(player, packet(finalMakeUpDay, true, ESignInType.RE_SIGN_IN)));
        require(data.getTotalSignInDays() == before + 2 && data.isRewardedOn(finalMakeUpDay)
                && data.getSignInCard() == CYCLES - cycles - 1, "Make-up summary or reward state invalid");
        assertInventory(player, 10, 2, 2 * (REWARDS_PER_CLAIM - 2));

        measure("command-reward", () -> require(RewardManager.giveRewardToPlayer(player, data, commandReward()),
                "Command reward was rejected"));
        assertInventory(player, 10, 3, 2 * (REWARDS_PER_CLAIM - 2));
        measure("save-and-sync", () -> SakuraPlayerData.saveAndSync(player));
        cycles++;
    }

    void report() {
        for (Map.Entry<String, SakuraNetworkSmokeTimings> entry : timings.entrySet()) {
            require(entry.getValue().count() == CYCLES, "Incomplete timed workload " + entry.getKey());
            SakuraNetworkSmokeStatus.append("PASS operation " + entry.getKey() + " rewards-per-claim="
                    + REWARDS_PER_CLAIM + " delivery=inventory " + entry.getValue().summary());
        }
    }

    boolean complete() { return cycles == CYCLES; }
    int cycles() { return cycles; }
    Date finalSignedDay() { return finalSignedDay; }
    Date finalMakeUpDay() { return finalMakeUpDay; }

    static void assertAllRecordedRewards(IPlayerSignInData data, Date lastSignedDay) {
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            Date day = DateUtils.addDay(lastSignedDay, -3 * cycle);
            assertRecordedRewards(data, day);
            assertRecordedRewards(data, DateUtils.addDay(day, -1));
        }
    }

    private void measure(String name, Runnable action) {
        long startedAt = System.nanoTime();
        try {
            action.run();
        } catch (RuntimeException error) {
            SakuraNetworkSmokeStatus.append("FAIL operation " + name + " cycle=" + (cycles + 1)
                    + " rewards-per-claim=" + REWARDS_PER_CLAIM + " wall-ns=" + (System.nanoTime() - startedAt)
                    + " error=" + error);
            throw error;
        }
        timings.get(name).record(System.nanoTime() - startedAt);
    }

    private static Reward commandReward() {
        return new Reward("give @s minecraft:gold_nugget 1", SakuraRewardTypes.COMMAND);
    }

    private static SignInPacket packet(Date day, boolean autoReward, ESignInType type) {
        return new SignInPacket(DateUtils.toDateTimeString(day), autoReward, type);
    }

    static void assertInventory(ServerPlayer player, int apples, int gold, int carrots) {
        int actualApples = count(player, Items.APPLE);
        int actualGold = count(player, Items.GOLD_NUGGET);
        int actualCarrots = count(player, Items.CARROT);
        require(actualApples == apples && actualGold == gold && actualCarrots == carrots,
                "Unexpected delivered reward quantities: apples=" + actualApples + "/" + apples
                        + ", gold=" + actualGold + "/" + gold + ", carrots=" + actualCarrots + "/" + carrots);
    }

    static void assertRecordedRewards(IPlayerSignInData data, Date day) {
        java.util.List<SignInRecord> records = new java.util.ArrayList<>();
        for (SignInRecord record : data.getSignInRecords()) {
            if (DateUtils.toDateInt(record.getCompensateTime()) == DateUtils.toDateInt(day)) records.add(record);
        }
        require(records.size() == 1 && data.isSignedOn(day) && data.isRewardedOn(day), "Invalid final day state");
        SignInRecord record = records.get(0);
        require(record.isRewarded() && record.getRewardList().size() == REWARDS_PER_CLAIM,
                "Final history detail or reward snapshot was lost");
        java.util.List<Reward> expected = createRewardConfig(day).getBaseRewards();
        for (int index = 0; index < expected.size(); index++) {
            Reward actual = record.getRewardList().get(index);
            Reward value = expected.get(index);
            require(actual.isRewarded() && value.getTypeId().equals(actual.getTypeId())
                    && value.getContent().equals(actual.getContent())
                    && value.getProbability().compareTo(actual.getProbability()) == 0,
                    "Final history reward content or state was not persisted");
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.inventory.items) if (stack.getItem() == item) total += stack.getCount();
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
