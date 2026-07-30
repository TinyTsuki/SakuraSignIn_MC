package xin.vanilla.sakura.config;

import org.junit.Test;
import xin.vanilla.banira.common.config.annotation.ConfigEntry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 所有可编辑叶子配置都应提供玩家可读说明，不能让界面回退显示配置键名。
 */
public class ConfigTooltipCoverageTest {
    @Test
    public void clientAndCommonLeavesHaveDescriptions() {
        assertCategoryTooltips(ClientConfig.DisplayCategory.class);
        assertCategoryTooltips(ClientConfig.RewardKeysCategory.class);
        assertCategoryTooltips(ClientConfig.SignKeysCategory.class);
        assertCategoryTooltips(CommonConfig.MakeUpCategory.class);
        assertCategoryTooltips(CommonConfig.CoolingCategory.class);
        assertCategoryTooltips(CommonConfig.DateTimeCategory.class);
        assertCategoryTooltips(CommonConfig.RewardCategory.class);
        assertCategoryTooltips(CommonConfig.ServerCategory.class);
        assertCategoryTooltips(CommonConfig.HistoryCategory.class);
        assertCategoryTooltips(CommonConfig.CommandCategory.class);
        assertCategoryTooltips(CommonConfig.ConciseCategory.class);
        assertCategoryTooltips(CommonConfig.PermissionCategory.class);
    }

    private static void assertCategoryTooltips(Class<?> category) {
        for (Field field : category.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            ConfigEntry.Gui.Tooltip tooltip =
                    field.getAnnotation(ConfigEntry.Gui.Tooltip.class);
            assertNotNull(category.getSimpleName() + "." + field.getName(), tooltip);
            assertTrue(category.getSimpleName() + "." + field.getName() + " zh_cn",
                    !tooltip.zh_cn().trim().isEmpty());
            assertTrue(category.getSimpleName() + "." + field.getName() + " en_us",
                    !tooltip.en_us().trim().isEmpty());
        }
    }
}
