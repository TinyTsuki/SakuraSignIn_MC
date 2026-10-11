package xin.vanilla.sakura.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.*;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.api.BaniraCosts;
import xin.vanilla.banira.common.util.ItemUtils;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class LotteryPoolFormTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BaniraTestPlatform.install();
        BaniraTestPlatform.register("minecraft:paper", Items.PAPER);
    }

    @Test public void metadataFieldsRetainBoundsAndNewlines() throws Exception {
        LotteryPool pool = pool();
        pool.setDescription("First\nSecond");
        pool.setVoucherExpression("xpPoints(2)");
        InputFormScreen.Args args = form(pool, new AtomicReference<>());
        InputFormScreen.Widget description = widget(args, "description");
        assertEquals(2048, description.maxLength());
        assertEquals(pool.getDescription(), description.defaultValue());
        assertTrue(description.allowEmpty());
        InputFormScreen.Widget voucher = widget(args, "voucherExpression");
        assertEquals(4096, voucher.maxLength());
        assertTrue(voucher.allowEmpty());
        assertFalse(voucher.validator().apply(new InputFormScreen.Results()
                .value("voucherExpression", 0, "System.exit(0)").curName("voucherExpression")).isEmpty());
    }

    @Test public void submitPreservesMetadataAndDoesNotUploadInvalidExpression() throws Exception {
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
        submitted.set(null);
        results.value("voucherExpression", 0, "unknown(1)");
        args.getCallback().accept(results);
        assertNull(submitted.get());
    }

    @Test public void selectedItemsUseNativeSpecificationsAndExplicitMatchMode() {
        ItemStack paper = new ItemStack(Items.PAPER, 12);
        paper.getOrCreateTag().putString("label", "A\"B\\C");
        String exact = LotteryVoucherExpressions.item(paper, 2, true);
        String idOnly = LotteryVoucherExpressions.item(paper, 2, false);
        assertEquals(ItemUtils.serializeItemStack(paper), BaniraCosts.parse(exact).resource().specification());
        assertEquals("minecraft:paper", BaniraCosts.parse(idOnly).resource().specification());
        assertEquals(12, paper.getCount());
        assertEquals(2, BaniraCosts.parse(exact).resource().amount());
    }

    @Test public void editingSyncedPoolCopyKeepsItsLockedIdValid() throws Exception {
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

    private static LotteryPool pool() {
        return new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 1, 0, new RewardList());
    }
    private static InputFormScreen.Widget widget(InputFormScreen.Args args, String name) {
        return args.getWidgets().stream().filter(widget -> name.equals(widget.name())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing field " + name));
    }
    private static InputFormScreen.Args form(LotteryPool pool, AtomicReference<LotteryPool> submit) throws Exception {
        Screen parent = new Screen(Component.literal("parent")) {};
        Screen screen = LotteryPoolForm.create(parent, pool, submit::set);
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        return (InputFormScreen.Args) field.get(screen);
    }
}
