package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.common.util.ItemUtils;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPools;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class LotteryPoolFormTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @Rule
    public org.junit.rules.TemporaryFolder temp = new org.junit.rules.TemporaryFolder();

    @BeforeClass
    public static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BaniraTestPlatform.install();
        BaniraTestPlatform.register("minecraft:paper", Items.PAPER);
    }

    @Test
    public void metadataFieldsRetainBoundsAndNewlines() throws Exception {
        LotteryPool pool = pool();
        pool.setDescription("First\nSecond");
        pool.setVoucherExpression("xpPoints(2)");
        InputFormScreen.Args args = form(pool, new AtomicReference<>());
        InputFormScreen.Widget description = widget(args, "description");
        assertEquals(2048, description.maxLength());
        assertEquals(pool.getDescription(), description.defaultValue());
        assertTrue(description.allowEmpty());
        InputFormScreen.Widget voucher = widget(args, "voucherExpression");
        assertTrue(voucher.pickerReadOnly());
        assertTrue(voucher.defaultValue().contains("2"));
        var editor = child(voucher, ignored -> {
        });
        assertEquals("xpPoints", editor.uiSession().draft("kind"));
        assertEquals("2", editor.uiSession().draft("amount"));
    }

    @Test
    public void submitPreservesMetadataAndDoesNotUploadInvalidExpression() throws Exception {
        LotteryPool pool = pool();
        pool.setDescription("One\nTwo");
        pool.setVoucherExpression("xpPoints(2)");
        AtomicReference<LotteryPool> submitted = new AtomicReference<>();
        InputFormScreen.Args args = form(pool, submitted);
        InputFormScreen.Results results = new InputFormScreen.Results();
        int index = 0;
        for (InputFormScreen.Widget widget : args.getWidgets()) {
            results.value(widget.name(), index++, widget.defaultValue());
        }
        args.getCallback().accept(results);
        assertEquals(pool, submitted.get());
        var editor = child(widget(args, "voucherExpression"), value -> fail("Invalid cost must not update the parent"));
        editor.uiSession().draft("kind", "advanced");
        editor.uiSession().draft("advanced", "unknown(1)");
        assertFalse(editor.uiSession().submit());
    }

    @Test
    public void buildsVoucherWithoutRequiringExpressionSyntax() {
        assertEquals("banira:item", BaniraCosts.parse(LotteryVoucherExpressions.build("item", "minecraft:paper", "", 3)).resource().resourceId());
        assertEquals("banira:xp_point", BaniraCosts.parse(LotteryVoucherExpressions.build("xpPoints", "", "", 3)).resource().resourceId());
        assertEquals("banira:xp_level", BaniraCosts.parse(LotteryVoucherExpressions.build("xpLevels", "", "", 3)).resource().resourceId());
        var score = BaniraCosts.parse(LotteryVoucherExpressions.build("score", "", "tokens", 3)).resource();
        assertEquals("banira:score", score.resourceId());
        assertEquals("tokens", score.specification());
        assertEquals(3, score.amount());
        assertThrows(IllegalArgumentException.class, () -> LotteryVoucherExpressions.build("item", "", "", 1));
    }

    @Test
    public void vouchersReopenWithOnlyTheSelectedInputsAndPreserveAdvancedExpressions() throws Exception {
        for (String expression : java.util.List.of("", "item(\"minecraft:paper\",3)", "xpPoints(7)", "xpLevels(8)",
                "score(\"tokens\",9)", "itemExact(\"minecraft:paper\",2)", "xpPoints(2) && score(\"tokens\",1)")) {
            LotteryPool pool = pool();
            pool.setVoucherExpression(expression);
            AtomicReference<LotteryPool> result = new AtomicReference<>();
            var form = form(pool, result);
            var editor = child(widget(form, "voucherExpression"), ignored -> {
            });
            var ui = editor.uiSession();
            var expected = LotteryVoucherExpressions.read(expression);
            assertEquals(expected.kind(), ui.draft("kind"));
            assertEquals(switch (expected.kind()) {
                        case "item", "score" -> 3;
                        case "free" -> 1;
                        default -> 2;
                    },
                    ui.visibleFieldCount());
            boolean accepted = ui.submit();
            assertTrue(expression + " (" + expected.kind() + "): " + ui.error() + " " + ui.errors(), accepted);
            form.getCallback().accept(defaults(form));
            assertEquals(expression, result.get().getVoucherExpression());
        }
    }

    @Test
    public void artworkShowsTheSavedSourceAndOnlyCommitsTheSelectedValue() throws Exception {
        var pool = pool();
        pool.setArtworkItem("minecraft:paper");
        AtomicReference<LotteryPool> submitted = new AtomicReference<>();
        var args = form(pool, submitted);
        var parentValue = new AtomicReference<String>();
        var editor = child(widget(args, "poster"), parentValue::set);
        var ui = editor.uiSession();
        assertEquals("item", ui.draft("mode"));
        assertEquals("minecraft:paper", ui.draft("item"));
        assertEquals(2, ui.visibleFieldCount());
        ui.draft("mode", "png");
        assertFalse(ui.canSubmit());
        ui.draft("mode", "texture");
        ui.draft("texture", "example:textures/lottery/spring.png");
        ui.draft("png", "invalid.png");
        assertTrue(ui.submit());
        assertTrue(parentValue.get().contains("example:textures/lottery/spring.png"));
        args.getCallback().accept(defaults(args));
        assertEquals("", submitted.get().getArtworkItem());
        assertEquals("", submitted.get().getPosterPng());
        var reopened = child(widget(form(submitted.get(), new AtomicReference<>()), "poster"), ignored -> {
        });
        assertEquals("texture", reopened.uiSession().draft("mode"));
        assertEquals("example:textures/lottery/spring.png", reopened.uiSession().draft("texture"));
    }

    @Test
    public void savedPngReopensWithPreviewAndNeedsNoLocalPath() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", bytes);
        String png = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        var pool = pool();
        pool.setPosterPng(png);
        pool.setThumbnailPng(png);
        var submitted = new AtomicReference<LotteryPool>();
        var form = form(pool, submitted);
        for (String key : java.util.List.of("poster", "thumbnail")) {
            assertTrue(widget(form, key).defaultValue().contains("4 × 2"));
            var editor = child(widget(form, key), ignored -> {
            });
            var ui = editor.uiSession();
            assertEquals("png", ui.draft("mode"));
            assertEquals("PNG · 4 × 2", ui.draft("savedPng"));
            assertNotNull(widget(ui.args(), "savedPng").preview());
            assertEquals(3, ui.visibleFieldCount());
            assertEquals("", ui.draft("png"));
            assertTrue(ui.submit());
        }
        form.getCallback().accept(defaults(form));
        assertEquals(png, submitted.get().getPosterPng());
        assertEquals(png, submitted.get().getThumbnailPng());
    }

    @Test
    public void invalidReplacementAndCancelDoNotEraseStoredArtwork() throws Exception {
        var pool = pool();
        pool.setArtworkItem("minecraft:paper");
        var submitted = new AtomicReference<LotteryPool>();
        var form = form(pool, submitted);
        var cancelled = child(widget(form, "poster"), ignored -> fail("Cancel must not update artwork"));
        cancelled.uiSession().draft("mode", "none");
        cancelled.uiSession().close(xin.vanilla.banira.api.client.ui.UiCloseReason.CANCEL);

        var file = temp.newFile("poster.png");
        var editor = child(widget(form, "poster"), ignored -> {
        });
        editor.uiSession().draft("mode", "png");
        editor.uiSession().draft("png", file.getAbsolutePath());
        assertFalse(editor.uiSession().submit());
        assertEquals("item", child(widget(form, "poster"), ignored -> {
        }).uiSession().draft("mode"));

        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 6, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", file);
        assertTrue(editor.uiSession().submit());
        form.getCallback().accept(defaults(form));
        assertEquals("", submitted.get().getArtworkItem());
        var reopened = child(widget(form(submitted.get(), new AtomicReference<>()), "poster"), ignored -> {
        });
        assertEquals("png", reopened.uiSession().draft("mode"));
        assertEquals("PNG · 8 × 6", reopened.uiSession().draft("savedPng"));
        assertEquals("", reopened.uiSession().draft("png"));
    }

    @Test
    public void selectedItemsUseNativeSpecificationsAndExplicitMatchMode() {
        ItemStack paper = new ItemStack(Items.PAPER, 12);
        paper.getOrCreateTag().putString("label", "A\"B\\C");
        String exact = LotteryVoucherExpressions.item(paper, 2, true);
        String idOnly = LotteryVoucherExpressions.item(paper, 2, false);
        assertEquals(ItemUtils.serializeItemStack(paper), BaniraCosts.parse(exact).resource().specification());
        assertEquals("minecraft:paper", BaniraCosts.parse(idOnly).resource().specification());
        assertEquals(12, paper.getCount());
        assertEquals(2, BaniraCosts.parse(exact).resource().amount());
    }

    @Test
    public void validPoolSubmissionIsConsumedOnlyOnceAcrossSharedViews() throws Exception {
        java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
        Screen parent = new Screen(net.minecraft.network.chat.Component.empty()) {
        };
        Screen screen = LotteryPoolForm.create(parent, pool(), value -> submissions.incrementAndGet());
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        InputFormScreen.Args args = (InputFormScreen.Args) field.get(screen);
        InputFormScreen.Results results = new InputFormScreen.Results();
        int index = 0;
        for (var widget : args.getWidgets()) results.value(widget.name(), index++, widget.defaultValue());
        args.getCallback().accept(results);
        args.getCallback().accept(results);
        assertEquals(1, submissions.get());
    }

    @Test
    public void editingSyncedPoolCopyKeepsItsLockedIdValid() throws Exception {
        xin.vanilla.sakura.config.reward.RewardConfig previous =
                xin.vanilla.sakura.config.reward.RewardConfigManager.getRewardConfig();
        try {
            xin.vanilla.sakura.config.reward.RewardConfig config =
                    new xin.vanilla.sakura.config.reward.RewardConfig();
            LotteryPool current = pool();
            config.getLotteryPools().add(current);
            xin.vanilla.sakura.config.reward.RewardConfigManager.setRewardConfig(config);
            LotteryPool copied = LotteryPools.copy(current);
            InputFormScreen.Widget id = widget(form(copied, new AtomicReference<>()), "id");
            assertEquals("", id.validator().apply(new InputFormScreen.Results()
                    .value("id", 0, "daily").curName("id")));
            assertFalse(id.validator().apply(new InputFormScreen.Results()
                    .value("id", 0, "different").curName("id")).isEmpty());
            InputFormScreen.Widget newId = widget(form(null, new AtomicReference<>()), "id");
            assertFalse(newId.validator().apply(new InputFormScreen.Results()
                    .value("id", 0, "daily").curName("id")).isEmpty());
        } finally {
            xin.vanilla.sakura.config.reward.RewardConfigManager.setRewardConfig(previous);
        }
    }

    @Test
    public void invalidIdRetainsDraftUntilCorrectedOnce() {
        correctedForm("bad/id", "Daily", "id", "new-pool");
    }

    @Test
    public void whitespaceNameRetainsDraftUntilCorrectedOnce() {
        correctedForm("new-pool", " \t ", "name", "Daily");
    }

    @Test
    public void idValidatorRejectsInvalidSyntaxAndLength() throws Exception {
        InputFormScreen.Widget id = widget(form(null, new AtomicReference<>()), "id");
        for (String value : java.util.List.of("bad/id", "BAD", "x".repeat(65))) {
            assertFalse(id.validator().apply(new InputFormScreen.Results().value("id", 0, value).curName("id")).isEmpty());
        }
        assertEquals("", id.validator().apply(new InputFormScreen.Results().value("id", 0, "new.pool-1").curName("id")));
    }

    @Test
    public void callbackReportsInvalidCandidateInsteadOfSilentSuccess() throws Exception {
        AtomicReference<LotteryPool> submitted = new AtomicReference<>();
        InputFormScreen.Args args = form(pool(), submitted);
        InputFormScreen.Results result = defaults(args).value("name", 1, "   ");
        args.getCallback().accept(result);
        assertNull(submitted.get());
        assertNotNull("Invalid candidate must report failure", result.runningResult());
        assertFalse(result.runningResult().isBlank());
        args.getCallback().accept(defaults(args));
        assertNotNull(submitted.get());
    }

    private static void correctedForm(String id, String name, String field, String correction) {
        java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
        Screen parent = new Screen(net.minecraft.network.chat.Component.literal("parent")) {
        };
        InputFormScreen form = (InputFormScreen) LotteryPoolForm.create(parent, null, value -> submissions.incrementAndGet());
        var session = form.uiSession();
        session.draft("id", id);
        session.draft("name", name);
        assertFalse("Invalid input must prevent submission", session.canSubmit());
        assertFalse(session.submit());
        assertFalse(session.finished());
        assertEquals(0, submissions.get());
        assertEquals(field.equals("id") ? id : name, session.draft(field));
        session.draft(field, correction);
        assertTrue(session.submit());
        assertEquals(1, submissions.get());
        assertFalse(session.submit());
        assertEquals(1, submissions.get());
    }

    private static InputFormScreen.Results defaults(InputFormScreen.Args args) {
        InputFormScreen.Results result = new InputFormScreen.Results();
        int index = 0;
        for (InputFormScreen.Widget widget : args.getWidgets())
            result.value(widget.name(), index++, widget.defaultValue());
        return result;
    }

    private static LotteryPool pool() {
        return new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 1, 0, new RewardList());
    }

    private static InputFormScreen.Widget widget(InputFormScreen.Args args, String name) {
        return args.getWidgets().stream().filter(widget -> name.equals(widget.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing field " + name));
    }

    private static InputFormScreen.Args form(LotteryPool pool, AtomicReference<LotteryPool> submit) throws Exception {
        Screen parent = new Screen(net.minecraft.network.chat.Component.literal("parent")) {
        };
        Screen screen = LotteryPoolForm.create(parent, pool, submit::set);
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        return (InputFormScreen.Args) field.get(screen);
    }

    private static InputFormScreen child(InputFormScreen.Widget widget, java.util.function.Consumer<String> selected) {
        return (InputFormScreen) widget.picker().apply(new Screen(net.minecraft.network.chat.Component.empty()) {
        }, selected);
    }
}
