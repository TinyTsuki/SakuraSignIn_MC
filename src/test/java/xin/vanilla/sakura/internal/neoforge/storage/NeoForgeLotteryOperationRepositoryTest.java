package xin.vanilla.sakura.internal.neoforge.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.data.lottery.LotteryOperation;
import xin.vanilla.sakura.data.lottery.LotteryOperationCodec;
import xin.vanilla.sakura.data.lottery.LotteryOperationTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class NeoForgeLotteryOperationRepositoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private NeoForgeLotteryOperationRepository repository() {
        return new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath());
    }
    private static LotteryOperation completed(long sequence) {
        return LotteryOperationTest.prepared(sequence).paying().paid(LotteryOperationTest.receipt())
                .selectRewards().granting().grantSucceeded().granting().grantSucceeded().complete();
    }
    private static void complete(NeoForgeLotteryOperationRepository repo, long sequence) throws IOException {
        LotteryOperation op = LotteryOperationTest.prepared(sequence); repo.write(op);
        op = op.paying(); repo.write(op);
        op = op.paid(LotteryOperationTest.receipt()); repo.write(op);
        op = op.selectRewards(); repo.write(op);
        op = op.granting(); repo.write(op);
        op = op.grantSucceeded(); repo.write(op);
        op = op.granting(); repo.write(op);
        repo.write(op = op.grantSucceeded()); repo.write(op.complete());
    }
    @Test public void normalReopenKeepsReceiptSeedAndConfirmedGrantPrefix() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op); repo.write(op = op.paying()); repo.write(op = op.paid(LotteryOperationTest.receipt()));
        repo.write(op = op.selectRewards()); repo.write(op = op.granting()); repo.write(op = op.grantSucceeded());
        repo.flush();
        LotteryOperation restored = repository().load(LotteryOperationTest.PLAYER, op.getOperationId());
        assertEquals(1, restored.getGrantedCount());
        assertEquals(new LotteryOperationCodec().encode(op), new LotteryOperationCodec().encode(restored));
        assertEquals(1, repository().incomplete(LotteryOperationTest.PLAYER).size());
    }
    @Test public void incompleteAndUncertainOperationsBlockNewIds() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op); rejectIo(() -> repo.write(LotteryOperationTest.prepared(2)));
        repo.write(op = op.paying()); repo.write(op.uncertain());
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(3)));
        assertEquals(LotteryOperation.Stage.UNCERTAIN,
                repository().incomplete(LotteryOperationTest.PLAYER).get(0).getStage());
    }
    @Test public void terminalStateCannotBeOverwrittenOrPaidAgain() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); complete(repo, 1); repo.write(completed(1));
        rejectIo(() -> repo.write(LotteryOperationTest.prepared(1)));
        rejectIo(() -> repo.write(LotteryOperationTest.prepared(1).paying()));
        assertEquals(LotteryOperation.Stage.COMPLETED,
                repo.load(LotteryOperationTest.PLAYER, completed(1).getOperationId()).getStage());
    }
    @Test public void rejectsSkippingWriteAheadStatesAndChangingSnapshot() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation before = LotteryOperationTest.prepared(1);
        repo.write(before); rejectIo(() -> repo.write(before.paying().paid(LotteryOperationTest.receipt())));
        LotteryOperation changed = LotteryOperation.prepare(LotteryOperationTest.PLAYER, before.getRequest(),
                LotteryOperationTest.pool(), 2, 999, before.getCreatedAt(), before.getDisplayToken());
        rejectIo(() -> repo.write(changed));
        assertEquals(1, repo.load(LotteryOperationTest.PLAYER, before.getOperationId()).getSeed());
    }
    @Test public void retainsOneHundredCompletedAndRejectsPrunedReplayAfterReopen() throws Exception {
        for (int i = 1; i <= 101; i++) complete(repository(), i);
        assertNull(repository().load(LotteryOperationTest.PLAYER, LotteryOperationTest.prepared(1).getOperationId()));
        assertNotNull(repository().load(LotteryOperationTest.PLAYER, LotteryOperationTest.prepared(101).getOperationId()));
        assertEquals(100, repository().list(LotteryOperationTest.PLAYER).size());
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(1)));
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(100)));
        complete(repository(), 102); assertEquals(100, repository().list(LotteryOperationTest.PLAYER).size());
    }
    @Test public void newSessionCannotBypassPreviousSessionsIncompleteOperation() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op);
        LotteryOperation other = LotteryOperation.prepare(LotteryOperationTest.PLAYER,
                new xin.vanilla.sakura.data.lottery.LotteryDrawRequest(UUID.randomUUID(), 1, 3,
                        "daily", 2, op.getRequest().getQuoteToken()), LotteryOperationTest.pool(),
                2, 2, op.getCreatedAt(), op.getDisplayToken());
        rejectIo(() -> repo.write(other));
        assertEquals(op.getOperationId(), repository().incomplete(LotteryOperationTest.PLAYER).get(0).getOperationId());
    }
    @Test public void foreignPlayerAndPathTraversalCannotLoadOperation() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op); assertNull(repo.load(UUID.randomUUID(), op.getOperationId()));
        rejectIo(() -> repo.load(LotteryOperationTest.PLAYER, "../../outside"));
        assertEquals(1, repo.list(LotteryOperationTest.PLAYER).size());
    }
    @Test public void corruptedOperationIsPreservedAndFailsClosed() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op);
        Path file = temporary.getRoot().toPath().resolve("sakura_sign_in/lottery")
                .resolve(LotteryOperationTest.PLAYER.toString()).resolve(op.getOperationId() + ".json");
        byte[] malformed = "{\"stage\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8); Files.write(file, malformed);
        rejectIo(() -> repository().load(LotteryOperationTest.PLAYER, op.getOperationId()));
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(2)));
        assertArrayEquals(malformed, Files.readAllBytes(file));
    }
    @Test public void failedReplaceLeavesOriginalAndDoesNotPermitNewOperation() throws Exception {
        NeoForgeLotteryOperationRepository repo = repository(); LotteryOperation op = LotteryOperationTest.prepared(1);
        repo.write(op);
        Path file = temporary.getRoot().toPath().resolve("sakura_sign_in/lottery")
                .resolve(LotteryOperationTest.PLAYER.toString()).resolve(op.getOperationId() + ".json");
        byte[] before = Files.readAllBytes(file);
        NeoForgeLotteryOperationRepository failing = new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath(),
                (source, target) -> { throw new IOException("injected move"); });
        rejectIo(() -> failing.write(op.paying())); assertArrayEquals(before, Files.readAllBytes(file));
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(2)));
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
    @Test public void failedOperationCreationReservesSequenceWithoutCreatingDebitRecord() throws Exception {
        LotteryOperation op = LotteryOperationTest.prepared(1);
        NeoForgeLotteryOperationRepository failing = new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath(),
                (source, target) -> {
                    if (target.getFileName().toString().equals(op.getOperationId() + ".json"))
                        throw new IOException("injected operation write");
                    Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                });
        rejectIo(() -> failing.write(op));
        assertNull(repository().load(LotteryOperationTest.PLAYER, op.getOperationId()));
        rejectIo(() -> repository().write(op));
        repository().write(LotteryOperationTest.prepared(2));
        assertEquals(1, repository().list(LotteryOperationTest.PLAYER).size());
    }
    @Test public void corruptWatermarkNeverAllowsReplayedOrNewRequest() throws Exception {
        complete(repository(), 1);
        Path file = temporary.getRoot().toPath().resolve("sakura_sign_in/lottery")
                .resolve(LotteryOperationTest.PLAYER.toString()).resolve("_sessions")
                .resolve(LotteryOperationTest.SESSION + ".json");
        Files.write(file, "{}".getBytes(StandardCharsets.UTF_8));
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(2)));
        rejectIo(() -> repository().write(completed(1)));
        assertEquals("{}", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
    @Test public void ambiguousReadbackNeverReopensPreviouslyPersistedPayment() throws Exception {
        LotteryOperation op = LotteryOperationTest.prepared(1);
        repository().write(op);
        NeoForgeLotteryOperationRepository failing = new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath(),
                (source, target) -> {
                    Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    throw new IOException("injected after move");
                });
        rejectIo(() -> failing.write(op.paying()));
        LotteryOperation saved = repository().load(LotteryOperationTest.PLAYER, op.getOperationId());
        assertEquals(LotteryOperation.Stage.PAYING, saved.getStage());
        repository().write(saved.recover());
        rejectIo(() -> repository().write(LotteryOperationTest.prepared(2)));
        assertEquals(LotteryOperation.Stage.UNCERTAIN,
                repository().incomplete(LotteryOperationTest.PLAYER).get(0).getStage());
    }
    @Test public void metadataFailureIsNotReportedAsMissingOperation() throws Exception {
        NeoForgeLotteryOperationRepository invalidIo = new NeoForgeLotteryOperationRepository(
                AttributeFailurePath.deny(temporary.getRoot().toPath(), path -> true));
        rejectIo(() -> invalidIo.load(LotteryOperationTest.PLAYER,
                LotteryOperationTest.prepared(1).getOperationId()));
    }
    @Test public void inaccessibleReplayWatermarkIsPreservedAndCannotAdmitRequest() throws Exception {
        complete(repository(), 1);
        Path watermark = temporary.getRoot().toPath().resolve("sakura_sign_in/lottery")
                .resolve(LotteryOperationTest.PLAYER.toString()).resolve("_sessions")
                .resolve(LotteryOperationTest.SESSION + ".json");
        byte[] before = Files.readAllBytes(watermark);
        NeoForgeLotteryOperationRepository denied = new NeoForgeLotteryOperationRepository(
                AttributeFailurePath.deny(temporary.getRoot().toPath(), path -> path.equals(watermark)));
        rejectIo(() -> denied.write(LotteryOperationTest.prepared(2)));
        assertArrayEquals(before, Files.readAllBytes(watermark));
        assertNull(repository().load(LotteryOperationTest.PLAYER, LotteryOperationTest.prepared(2).getOperationId()));
    }
    @Test public void transientAccessDeniedCanReplaceVerifiedJournalWithoutDeletingOriginal() throws Exception {
        LotteryOperation op = LotteryOperationTest.prepared(1); repository().write(op);
        int[] denied = {2};
        NeoForgeLotteryOperationRepository retrying = new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath(),
                (source, target) -> {
                    if (denied[0]-- > 0) throw new java.nio.file.AccessDeniedException(target.toString());
                    Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                });
        retrying.write(op.paying());
        assertEquals(LotteryOperation.Stage.PAYING, repository().load(LotteryOperationTest.PLAYER, op.getOperationId()).getStage());
    }
    @Test public void permanentAccessDeniedPreservesOriginalJournal() throws Exception {
        LotteryOperation op = LotteryOperationTest.prepared(1); repository().write(op);
        NeoForgeLotteryOperationRepository denied = new NeoForgeLotteryOperationRepository(temporary.getRoot().toPath(),
                (source, target) -> { throw new java.nio.file.AccessDeniedException(target.toString()); });
        rejectIo(() -> denied.write(op.paying()));
        assertEquals(LotteryOperation.Stage.PREPARED, repository().load(LotteryOperationTest.PLAYER, op.getOperationId()).getStage());
    }
    private interface IoAction { void run() throws IOException; }
    private static void rejectIo(IoAction action) throws IOException {
        try { action.run(); fail("Unsafe store operation accepted"); } catch (IOException expected) { }
    }
}
