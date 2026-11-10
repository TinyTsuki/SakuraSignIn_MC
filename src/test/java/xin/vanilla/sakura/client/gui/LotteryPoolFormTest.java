package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Bootstrap;
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
    @Test
    public void reopeningArtworkShowsOnlyTheSavedSourceAndPreservesItsValue() throws Exception {
        LotteryPool pool = pool();
        pool.setArtworkTexture("test:textures/lottery/spring.png");
        AtomicReference<LotteryPool> submitted = new AtomicReference<>();
        InputFormScreen.Args args = form(pool, submitted);
        Screen owner = new Screen(new net.minecraft.util.text.StringTextComponent("")) {
        };
        lombok.var poster = widget(args, "poster");
        InputFormScreen editor = (InputFormScreen) poster.picker().apply(owner, ignored -> {
        });
        assertEquals("texture", editor.uiSession().draft("mode"));
        assertEquals(pool.getArtworkTexture(), editor.uiSession().draft("texture"));
        assertEquals(2, editor.uiSession().visibleFieldCount());
        editor.uiSession().draft("mode", "none");
        assertEquals(1, editor.uiSession().visibleFieldCount());
        editor.uiSession().draft("mode", "texture");
        assertEquals(pool.getArtworkTexture(), editor.uiSession().draft("texture"));
        assertTrue(editor.uiSession().submit());

        InputFormScreen thumbnail = (InputFormScreen) widget(args, "thumbnail").picker().apply(owner, ignored -> {
        });
        assertEquals("auto", thumbnail.uiSession().draft("mode"));
        assertEquals(1, thumbnail.uiSession().visibleFieldCount());
        InputFormScreen.Results results = new InputFormScreen.Results();
        int index = 0;
        for (lombok.var field : args.getWidgets()) results.value(field.name(), index++, field.defaultValue());
        args.getCallback().accept(results);
        assertEquals(pool, submitted.get());
    }

    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
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
        Screen owner = new Screen(new net.minecraft.util.text.StringTextComponent("")) {
        };
        InputFormScreen editor = (InputFormScreen) voucher.picker().apply(owner, ignored -> {
        });
        assertEquals("xpPoints", editor.uiSession().draft("kind"));
        assertEquals("2", editor.uiSession().draft("amount"));
        assertEquals(2, editor.uiSession().visibleFieldCount());
        editor.uiSession().draft("kind", "score");
        assertEquals(3, editor.uiSession().visibleFieldCount());
        editor.uiSession().draft("objective", "draws");
        editor.uiSession().draft("amount", "7");
        assertTrue(editor.uiSession().submit());
        InputFormScreen reopened = (InputFormScreen) voucher.picker().apply(owner, ignored -> {
        });
        assertEquals("score", reopened.uiSession().draft("kind"));
        assertEquals("draws", reopened.uiSession().draft("objective"));
        assertEquals("7", reopened.uiSession().draft("amount"));
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
        assertEquals("xpPoints(2)", submitted.get().getVoucherExpression());
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
        Screen parent = new Screen(new net.minecraft.util.text.StringTextComponent("")) {
        };
        Screen screen = LotteryPoolForm.create(parent, pool(), value -> submissions.incrementAndGet());
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        InputFormScreen.Args args = (InputFormScreen.Args) field.get(screen);
        InputFormScreen.Results results = new InputFormScreen.Results();
        int index = 0;
        for (lombok.var widget : args.getWidgets()) results.value(widget.name(), index++, widget.defaultValue());
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
        for (String value : com.google.common.collect.ImmutableList.of("bad/id", "BAD", String.join("", java.util.Collections.nCopies(65, "x")))) {
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
        assertFalse(result.runningResult().codePoints().allMatch(Character::isWhitespace));
        args.getCallback().accept(defaults(args));
        assertNotNull(submitted.get());
    }

    private static void correctedForm(String id, String name, String field, String correction) {
        java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
        Screen parent = new Screen(new net.minecraft.util.text.StringTextComponent("parent")) {
        };
        InputFormScreen form = (InputFormScreen) LotteryPoolForm.create(parent, null, value -> submissions.incrementAndGet());
        lombok.var session = form.uiSession();
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
        Screen parent = new Screen(new net.minecraft.util.text.StringTextComponent("parent")) {
        };
        Screen screen = LotteryPoolForm.create(parent, pool, submit::set);
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        return (InputFormScreen.Args) field.get(screen);
    }
}
