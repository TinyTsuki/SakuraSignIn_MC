package xin.vanilla.sakura.reward.lottery;

import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.test.NativeBootstrap;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class LotteryRewardMessageTest {
    @BeforeClass
    public static void bootstrap() {
        NativeBootstrap.initialize();
    }

    @Test
    public void nativeRewardNamesRemainClientTranslatableInsideTheServerTemplate() {
        Component original = BaniraComponent.get().object(net.minecraft.network.chat.Component.translatable("item.minecraft.diamond"))
                .append("x1");
        String before = original.toJson().toString();
        Reward reward = named(original);
        Component message = LotteryDrawDispatcher.successMessage("zh_cn", Arrays.asList(reward, reward));
        String wire = BaniraComponent.get().object(message.toChat("zh_cn")).toJson().get("original").toString();
        assertTrue(wire, wire.contains("\"translate\":\"item.minecraft.diamond\""));
        assertEquals(wire, 2, wire.split("item.minecraft.diamond", -1).length - 1);
        assertFalse(wire, wire.contains("format.sakura_sign_in.lottery_draw_success_s"));
        assertTrue(message.toChat("zh_cn").getString().contains(", "));
        assertEquals(before, original.toJson().toString());
    }

    @Test
    public void literalAndEmptyRewardListsDoNotLeakServerTranslationKeys() {
        for (java.util.List<Reward> rewards : Arrays.asList(Collections.<Reward>emptyList(),
                Collections.singletonList(named(BaniraComponent.get().literal("custom reward"))))) {
            String wire = BaniraComponent.get().object(LotteryDrawDispatcher.successMessage("en_us", rewards)
                    .toChat("en_us")).toJson().get("original").toString();
            assertFalse(wire, wire.contains("format.sakura_sign_in.lottery_draw_success_s"));
        }
    }

    private static Reward named(Component name) {
        return new Reward() {
            @Override
            public Component getName(String language, boolean withAmount) {
                return name;
            }
        };
    }
}
