package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.client.gui.screens.Screen;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiPageSource;
import xin.vanilla.banira.client.gui.AdvancementSelectScreen;
import xin.vanilla.banira.client.gui.EffectSelectScreen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.ItemSelectScreen;
import xin.vanilla.sakura.api.reward.client.RewardEditContext;
import xin.vanilla.sakura.client.gui.AdvancementRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.EffectRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.ItemRewardSelectionFlow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class BuiltInRewardUiFormTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        xin.vanilla.sakura.client.ui.SignInUiParityTest.bootstrap();
    }

    private static final Screen PARENT = new Screen(new net.minecraft.network.chat.TextComponent("")) {
    };

    private static InputFormScreen.Args args(Screen screen) throws Exception {
        assertNotNull(((UiPageSource) screen).uiPage());
        Field field = InputFormScreen.class.getDeclaredField("args");
        field.setAccessible(true);
        return (InputFormScreen.Args) field.get(screen);
    }

    private static Screen form(String name, RewardEditContext<?> context, String... title) throws Exception {
        Method method;
        try {
            method = title.length == 0 ? BuiltInRewardEditors.class.getDeclaredMethod(name, RewardEditContext.class)
                    : BuiltInRewardEditors.class.getDeclaredMethod(name, RewardEditContext.class, String.class);
        } catch (NoSuchMethodException absent) {
            fail("Missing reusable form factory " + name);
            return null;
        }
        method.setAccessible(true);
        return (Screen) (title.length == 0 ? method.invoke(null, context) : method.invoke(null, context, title[0]));
    }

    @Test
    public void numericEditorsRestoreDefaultsRejectNonPositiveAndReturnIntegers() throws Exception {
        for (String title : new String[]{"enter_exp_point", "enter_exp_level", "enter_sign_in_card"}) {
            AtomicReference<Integer> submitted = new AtomicReference<>();
            RewardEditContext<Integer> context = new RewardEditContext<>(PARENT, 37, submitted::set, () -> {
            });
            InputFormScreen.Args args = args(form("positiveIntegerScreen", context, title));
            assertEquals("37", args.getWidgets().get(0).defaultValue());
            InputFormScreen.Results invalid = new InputFormScreen.Results().value("field_0", 0, "0");
            args.getCallback().accept(invalid);
            assertNull(submitted.get());
            assertFalse(invalid.runningResult().isEmpty());
            InputFormScreen.Results valid = new InputFormScreen.Results().value("field_0", 0, "12");
            args.getCallback().accept(valid);
            assertEquals(Integer.valueOf(12), submitted.get());
            assertEquals("1", args(form("positiveIntegerScreen", new RewardEditContext<Integer>(PARENT, null, submitted::set, () -> {
            }), title)).getWidgets().get(0).defaultValue());
        }
    }

    @Test
    public void messageAndCommandKeepExactTextAndCommandValidation() throws Exception {
        AtomicReference<String> command = new AtomicReference<>();
        InputFormScreen.Args args = args(form("commandScreen", new RewardEditContext<String>(PARENT, "/say old", command::set, () -> {
        })));
        assertEquals("/say old", args.getWidgets().get(0).defaultValue());
        InputFormScreen.Results invalid = new InputFormScreen.Results().value("field_0", 0, "say invalid");
        args.getCallback().accept(invalid);
        assertNull(command.get());
        assertFalse(invalid.runningResult().isEmpty());
        args.getCallback().accept(new InputFormScreen.Results().value("field_0", 0, "/say <literal>"));
        assertEquals("/say <literal>", command.get());
        AtomicReference<xin.vanilla.banira.common.data.Component> message = new AtomicReference<>();
        lombok.var initial = xin.vanilla.sakura.SakuraComponent.get().literal("old");
        InputFormScreen.Args messageArgs = args(form("messageScreen", new RewardEditContext<>(PARENT, initial, message::set, () -> {
        })));
        assertEquals(initial.toString(), messageArgs.getWidgets().get(0).defaultValue());
        messageArgs.getCallback().accept(new InputFormScreen.Results().value("field_0", 0, "<script>not executable</script>"));
        assertEquals("<script>not executable</script>", message.get().toString());
    }

    @Test
    public void threeSelectorsKeepSharedPagesAndCopiedInitialValues() throws Exception {
        lombok.var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
        ItemSelectScreen item = (ItemSelectScreen) ItemRewardSelectionFlow.create(PARENT, stack, ignored -> {
        });
        assertNotNull(((UiPageSource) item).uiPage());
        Field itemArgs = ItemSelectScreen.class.getDeclaredField("args");
        itemArgs.setAccessible(true);
        lombok.var copy = ((ItemSelectScreen.Args) itemArgs.get(item)).defaultItem();
        assertNotSame(stack, copy);
        assertEquals(7, copy.getCount());
        lombok.var effect = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LUCK, 1234, 3, true, false, false);
        EffectSelectScreen effects = (EffectSelectScreen) EffectRewardSelectionFlow.create(PARENT, effect, ignored -> {
        });
        assertNotNull(((UiPageSource) effects).uiPage());
        Field effectArgs = EffectSelectScreen.class.getDeclaredField("args");
        effectArgs.setAccessible(true);
        lombok.var effectCopy = ((EffectSelectScreen.Args) effectArgs.get(effects)).defaultEffect();
        assertNotSame(effect, effectCopy);
        assertEquals(1234, effectCopy.getDuration());
        assertEquals(3, effectCopy.getAmplifier());
        assertFalse(effectCopy.showIcon());
        lombok.var id = new net.minecraft.resources.ResourceLocation("minecraft:story/root");
        AdvancementSelectScreen advancement = (AdvancementSelectScreen) AdvancementRewardSelectionFlow.create(PARENT, id, ignored -> {
        });
        assertNotNull(((UiPageSource) advancement).uiPage());
        Field advancementArgs = AdvancementSelectScreen.class.getDeclaredField("args");
        advancementArgs.setAccessible(true);
        assertEquals(id, ((AdvancementSelectScreen.Args) advancementArgs.get(advancement)).defaultAdvancement());
    }
}
