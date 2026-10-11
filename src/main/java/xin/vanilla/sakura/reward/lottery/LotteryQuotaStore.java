package xin.vanilla.sakura.reward.lottery;

import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Quota settlement uses the same full-summary storage boundary as sign-in data. */
public final class LotteryQuotaStore {
    private final PlayerSummaryStore summaries;
    public LotteryQuotaStore(PlayerSummaryStore summaries) { this.summaries = Objects.requireNonNull(summaries); }
    public void save(UUID player, LotteryDrawState expected, Runnable cacheSave, Runnable sync) throws IOException {
        cacheSave.run();
        xin.vanilla.sakura.data.player.PlayerSignInSummary summary = summaries.load(player)
                .orElseThrow(() -> new IOException("Lottery quota summary is missing"));
        if (summary.getLotteryDrawStates().stream().noneMatch(expected::equals))
            throw new IOException("Lottery quota changed during summary save");
        summaries.saveAndVerify(player, summary);
        sync.run();
    }
}
