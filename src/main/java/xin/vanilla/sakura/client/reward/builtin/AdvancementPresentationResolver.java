package xin.vanilla.sakura.client.reward.builtin;

import lombok.Value;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.network.data.AdvancementData;

/** 将同步的进度展示数据转换为稳定的奖励图标与名称。 */
final class AdvancementPresentationResolver {
    private AdvancementPresentationResolver() {
    }

    static ResolvedDisplay resolve(ResourceLocation id) {
        AdvancementData data = SakuraClientState.getAdvancementData().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst().orElse(null);
        DisplayInfo display = data == null ? null : data.getDisplayInfo();
        if (display == null || display.getIcon().isEmpty()) {
            return new ResolvedDisplay(new ItemStack(Items.KNOWLEDGE_BOOK),
                    SakuraComponent.get().transClient("word", "reward_type_6"));
        }
        Component name = display.getTitle() == null
                ? SakuraComponent.get().transClient("word", "reward_type_6")
                : SakuraComponent.get().object(display.getTitle());
        return new ResolvedDisplay(display.getIcon().copy(), name);
    }

    static ResourceLocation fallbackIconId() {
        return new ResourceLocation("minecraft", "knowledge_book");
    }

    @Value
    static class ResolvedDisplay {
        ItemStack icon;
        Component name;
    }
}
