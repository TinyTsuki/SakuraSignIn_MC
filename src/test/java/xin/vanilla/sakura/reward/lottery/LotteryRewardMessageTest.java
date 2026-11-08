package xin.vanilla.sakura.reward.lottery;

import org.junit.Test;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class LotteryRewardMessageTest {
    @Test
    public void nativeRewardNamesRemainClientTranslatableInsideTheServerTemplate() {
        Component original = BaniraComponent.get().object(translated("item.minecraft.diamond")).append("x1");
        String before = original.toJson().toString();
        Reward reward = named(original);
        net.minecraft.network.chat.Component prepared = LotteryDrawDispatcher.successMessage("zh_cn", Arrays.asList(reward, reward)).toChat("zh_cn");
        assertEquals(Arrays.asList("item.minecraft.diamond", "item.minecraft.diamond"), keys(prepared));
        assertTrue(prepared.getString(), prepared.getString().startsWith("\u62bd\u5956\u83b7\u5f97 "));
        assertTrue(prepared.getString().contains(", "));
        assertEquals(before, original.toJson().toString());
    }

    @Test
    public void literalAndEmptyRewardListsDoNotLeakServerTranslationKeys() {
        for (List<Reward> rewards : Arrays.asList(Collections.<Reward>emptyList(),
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
                named(modName), named(BaniraComponent.get().object(translated("item.minecraft.diamond"))))).toChat("zh_cn");
        assertEquals(Collections.singletonList("item.minecraft.diamond"), keys(prepared));
        assertTrue(prepared.getString(), prepared.getString().contains("\u6b63\u5728\u62bd\u53d6"));
        assertTrue(prepared.getString().startsWith("\u62bd\u5956\u83b7\u5f97 "));
        assertEquals(before, modName.toJson().toString());
    }

    private static net.minecraft.network.chat.Component translated(String key) {
        return new net.minecraft.network.chat.TranslatableComponent(key);
    }

    private static List<String> keys(net.minecraft.network.chat.Component component) {
        List<String> keys = new ArrayList<>();
        Object contents = component;
        if (contents instanceof net.minecraft.network.chat.TranslatableComponent) {
            net.minecraft.network.chat.TranslatableComponent translated = (net.minecraft.network.chat.TranslatableComponent) contents;
            keys.add(translated.getKey());
            for (Object argument : translated.getArgs())
                if (argument instanceof net.minecraft.network.chat.Component)
                    keys.addAll(keys((net.minecraft.network.chat.Component) argument));
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) keys.addAll(keys(sibling));
        return keys;
    }

    private static Reward named(final Component name) {
        return new Reward() {
            @Override
            public Component getName(String language, boolean withAmount) {
                return name;
            }
        };
    }
}
