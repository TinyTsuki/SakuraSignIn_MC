package xin.vanilla.sakura.reward.lottery;

import org.junit.BeforeClass;
import org.junit.Test;
import net.minecraft.network.chat.contents.TranslatableContents;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.test.NativeBootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

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
        net.minecraft.network.chat.Component prepared = message.toChat("zh_cn");
        assertEquals(Arrays.asList("item.minecraft.diamond", "item.minecraft.diamond"), keys(prepared));
        assertTrue(prepared.getString(), prepared.getString().startsWith("抽奖获得 "));
        assertTrue(prepared.getString().contains(", "));
        assertEquals(before, original.toJson().toString());
    }

    @Test
    public void literalAndEmptyRewardListsDoNotLeakServerTranslationKeys() {
        for (java.util.List<Reward> rewards : Arrays.asList(Collections.<Reward>emptyList(),
                Collections.singletonList(named(BaniraComponent.get().literal("custom reward"))))) {
            net.minecraft.network.chat.Component prepared = LotteryDrawDispatcher.successMessage("en_us", rewards).toChat("en_us");
            assertTrue(keys(prepared).isEmpty());
            assertFalse(prepared.getString().contains("format.sakura_sign_in.lottery_draw_success_s"));
            if (!rewards.isEmpty()) assertTrue(prepared.getString().contains("custom reward"));
        }
    }

    @Test
    public void modRewardNamesAreResolvedOnTheServerAlongsideNativeItemNames() {
        Component modName = SakuraComponent.get().transLang("zh_cn", "word", "lottery_drawing");
        String before = modName.toJson().toString();
        net.minecraft.network.chat.Component prepared = LotteryDrawDispatcher.successMessage("zh_cn", Arrays.asList(
                        named(modName), named(BaniraComponent.get().object(net.minecraft.network.chat.Component.translatable("item.minecraft.diamond")))))
                .toChat("zh_cn");
        assertEquals(Collections.singletonList("item.minecraft.diamond"), keys(prepared));
        assertTrue(prepared.getString(), prepared.getString().contains("正在抽取"));
        assertTrue(prepared.getString().startsWith("抽奖获得 "));
        assertEquals(before, modName.toJson().toString());
    }

    private static List<String> keys(net.minecraft.network.chat.Component component) {
        List<String> keys = new ArrayList<>();
        if (component.getContents() instanceof TranslatableContents translated) {
            keys.add(translated.getKey());
            for (Object argument : translated.getArgs())
                if (argument instanceof net.minecraft.network.chat.Component nested) keys.addAll(keys(nested));
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) keys.addAll(keys(sibling));
        return keys;
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
