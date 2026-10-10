package xin.vanilla.sakura.reward.lottery;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class LotteryQuotaStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LotteryDrawState state(int count) {
        LotteryDrawState state = new LotteryDrawState("daily"); state.setPeriodDraws(count); state.setTotalDraws(count); return state;
    }
    @Test public void quotaSaveReturnsOnlyAfterVerifiedDiskPersistence() throws Exception {
        Store backend = new Store(); UUID player = UUID.randomUUID(); int[] synced = {0};
        new LotteryQuotaStore(backend).save(player, state(3), () -> backend.cache(state(3)), () -> synced[0]++);
        assertTrue(Files.isRegularFile(backend.path));
        assertEquals(3, PlayerSignInSummary.deserializeNBT(AtomicNbtFiles.read(backend.path))
                .getLotteryDrawStates().get(0).getTotalDraws());
        assertEquals(9, PlayerSignInSummary.deserializeNBT(AtomicNbtFiles.read(backend.path)).getSignInCard());
        assertEquals(1, synced[0]);
    }
    @Test public void diskFailurePreventsSuccessfulSettlementAndSync() throws Exception {
        Store backend = new Store(); backend.fail = true; int[] synced = {0};
        assertThrows(IOException.class, () -> new LotteryQuotaStore(backend).save(UUID.randomUUID(), state(3),
                () -> backend.cache(state(3)), () -> synced[0]++));
        assertEquals(0, synced[0]); assertFalse(Files.exists(backend.path));
    }
    @Test public void cacheReloadCannotSilentlyReplaceRequestedQuota() throws Exception {
        Store backend = new Store(); int[] synced = {0};
        assertThrows(IOException.class, () -> new LotteryQuotaStore(backend).save(UUID.randomUUID(), state(3),
                () -> backend.cache(state(1)), () -> synced[0]++));
        assertEquals(0, synced[0]); assertFalse(Files.exists(backend.path));
    }
    private class Store implements PlayerSummaryStore {
        final Path path = temporary.getRoot().toPath().resolve("summary.nbt");
        PlayerSignInSummary cached; boolean fail;
        void cache(LotteryDrawState state) {
            cached = new PlayerSignInSummary(); cached.setSignInCard(9); cached.getLotteryDrawStates().add(state);
        }
        public Optional<PlayerSignInSummary> load(UUID uuid) { return Optional.ofNullable(cached); }
        public void save(UUID uuid, PlayerSignInSummary summary) { cached = summary; }
        public void saveAndVerify(UUID uuid, PlayerSignInSummary summary) throws IOException {
            if (fail) throw new IOException("injected disk error");
            AtomicNbtFiles.write(path, summary.serializeNBT());
            if (!summary.serializeNBT().equals(AtomicNbtFiles.read(path))) throw new IOException("readback");
        }
    }
}
