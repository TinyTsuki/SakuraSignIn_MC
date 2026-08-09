package xin.vanilla.sakura.internal.client.dev;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraEnvironment;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraLang;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.collection.StringList;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.PersonalDateConfigScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.StringInputScreen;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仅在开发环境按显式目标打开界面，供有界 runClient 烟测使用。
 */
public final class SakuraUiSmokeRunner {
    public static final String ENVIRONMENT_KEY = "SAKURA_UI_SMOKE";
    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean opened;
    private static int themeIndex;
    private static int themeTicks;
    private static String previousThemeId;
    private static boolean previousSpecialVariant;
    private static boolean themeSmokeFinished;
    private static int rewardExtensionTicks;
    private static int personalDateTicks;

    private SakuraUiSmokeRunner() {
    }

    public static void tick() {
        if (BaniraEnvironment.isProduction()) {
            return;
        }

        String target = System.getenv(ENVIRONMENT_KEY);
        if ("language".equalsIgnoreCase(target)) {
            tickLanguage();
            return;
        }
        if ("theme-catalog".equalsIgnoreCase(target)) {
            tickThemeCatalog();
            return;
        }
        if (opened) {
            if ("reward-extension".equalsIgnoreCase(target)) {
                tickRewardExtensionExit();
            } else if ("personal-date".equalsIgnoreCase(target)) {
                tickPersonalDateExit();
            }
            return;
        }
        boolean rewardExtension = "reward-extension".equalsIgnoreCase(target);
        boolean reward = "reward".equalsIgnoreCase(target) || rewardExtension;
        boolean signIn = "sign-in".equalsIgnoreCase(target);
        boolean quickAction = "quick-action".equalsIgnoreCase(target);
        boolean inputForm = "input-form".equalsIgnoreCase(target);
        boolean personalDate = "personal-date".equalsIgnoreCase(target);
        if (!reward && !signIn && !quickAction && !inputForm && !personalDate) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        boolean inWorldWithoutScreen = !inputForm && minecraft.player != null
                && minecraft.level != null
                && parent == null;
        // 奖励配置依赖服务端下发的数据，只有输入表单可在主菜单独立验证。
        boolean atMainMenu = (inputForm || rewardExtension || personalDate)
                && parent instanceof MainMenuScreen;
        if (!inWorldWithoutScreen && !atMainMenu) {
            return;
        }

        opened = true;
        if (reward) {
            seedRewardExtensionSmokeData();
        } else if (personalDate) {
            seedPersonalDateSmokeData();
        }
        Screen screen = quickAction
                ? new InventoryScreen(minecraft.player)
                : signIn ? new SignInScreen()
                : personalDate ? new PersonalDateConfigScreen(parent)
                : inputForm ? inputForm(parent)
                : new RewardOptionScreen();
        if (screen instanceof SignInScreen) {
            ((SignInScreen) screen).previousScreen(parent);
        } else if (screen instanceof RewardOptionScreen) {
            ((RewardOptionScreen) screen).previousScreen(parent);
        }
        minecraft.setScreen(screen);
        LOGGER.info("Sakura UI smoke opened target: {}",
                quickAction ? "quick-action" : signIn ? "sign-in" : inputForm ? "input-form"
                        : personalDate ? "personal-date"
                        : rewardExtension ? "reward-extension" : "reward");
    }

    /** 在资源加载完成后验证模组语言发现与客户端语言选择。 */
    private static void tickLanguage() {
        if (opened) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof MainMenuScreen)) {
            return;
        }

        opened = true;
        List<String> languages = SakuraLang.get().getI18nFiles();
        String clientLanguage = SakuraLang.getClientLanguage();
        String chineseTitle = SakuraLang.get().getTranslation(
                "key.sakura_sign_in.categories", "zh_cn");
        if (!languages.contains("zh_cn")
                || !"zh_cn".equals(clientLanguage)
                || !"樱花签".equals(chineseTitle)) {
            throw new IllegalStateException("Sakura language smoke failed: languages="
                    + languages + ", client=" + clientLanguage + ", title=" + chineseTitle);
        }
        LOGGER.info("Sakura language smoke PASS: languages={}, client={}, title={}",
                languages, clientLanguage, chineseTitle);
        minecraft.stop();
    }

    private static void tickRewardExtensionExit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof RewardOptionScreen) || ++rewardExtensionTicks < 100) {
            return;
        }
        LOGGER.info("Sakura reward extension UI smoke PASS");
        minecraft.stop();
    }

    private static void tickPersonalDateExit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof PersonalDateConfigScreen)
                || ++personalDateTicks < 100) {
            return;
        }
        LOGGER.info("Sakura personal date UI smoke PASS");
        minecraft.stop();
    }

    private static void seedPersonalDateSmokeData() {
        java.util.Map<String, String> calendars = new java.util.LinkedHashMap<>();
        calendars.put(CalendarIds.GREGORIAN,
                "word.sakura_sign_in.calendar_gregorian");
        calendars.put(CalendarIds.CHINESE_LUNAR,
                "word.sakura_sign_in.calendar_chinese_lunar");
        SakuraClientState.setCalendarNames(calendars);
        RewardConfigManager.getRewardConfig().setPersonalDatePresets(
                java.util.Collections.singletonList(new PersonalDatePreset(
                        "annual", "Annual", PersonalDateRecurrence.YEARLY,
                        Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR),
                        3, PersonalDateDeliveryMode.SIGN_IN, 0, 1,
                        new xin.vanilla.sakura.reward.RewardList())));
    }

    /** 显式烟测只替换内存中的基础奖励，不触发配置保存。 */
    private static void seedRewardExtensionSmokeData() {
        JsonObject unknownPayload = new JsonObject();
        unknownPayload.addProperty("amount", 64);
        JsonObject invalidItemPayload = new JsonObject();

        List<Reward> fixtures = Arrays.asList(
                new Reward(new ItemStack(Items.APPLE, 5), SakuraRewardTypes.ITEM),
                new Reward(new EffectInstance(Effects.LUCK, 200, 0), SakuraRewardTypes.EFFECT),
                new Reward(5, SakuraRewardTypes.EXPERIENCE_POINT),
                new Reward(2, SakuraRewardTypes.EXPERIENCE_LEVEL),
                new Reward(1, SakuraRewardTypes.SIGN_IN_CARD),
                new Reward(new ResourceLocation("minecraft:story/root"),
                        SakuraRewardTypes.ADVANCEMENT),
                new Reward(SakuraComponent.get().literal("Smoke message"),
                        SakuraRewardTypes.MESSAGE),
                new Reward("say Sakura reward smoke", SakuraRewardTypes.COMMAND),
                new Reward(unknownPayload, RewardTypeId.of("example", "coin")),
                new Reward(invalidItemPayload, SakuraRewardTypes.ITEM)
        );
        RewardConfigManager.getRewardConfig().getBaseRewards().clear();
        RewardConfigManager.getRewardConfig().getBaseRewards().addAll(fixtures);

        List<RewardTypeId> actualTypes = fixtures.subList(0, 8).stream()
                .map(Reward::getTypeId)
                .collect(Collectors.toList());
        List<RewardTypeId> expectedTypes = Arrays.asList(
                SakuraRewardTypes.ITEM,
                SakuraRewardTypes.EFFECT,
                SakuraRewardTypes.EXPERIENCE_POINT,
                SakuraRewardTypes.EXPERIENCE_LEVEL,
                SakuraRewardTypes.SIGN_IN_CARD,
                SakuraRewardTypes.ADVANCEMENT,
                SakuraRewardTypes.MESSAGE,
                SakuraRewardTypes.COMMAND
        );
        if (!expectedTypes.equals(actualTypes)) {
            throw new IllegalStateException("Unexpected reward smoke fixture order: " + actualTypes);
        }
        try {
            RewardOperations.decode(fixtures.get(fixtures.size() - 1));
            throw new IllegalStateException("Invalid item payload was accepted");
        } catch (com.google.gson.JsonParseException expected) {
            LOGGER.info("Sakura reward extension fixture PASS: builtIns=8, unknown=1, invalid=1");
        }
    }

    /**
     * 在真实客户端中依次重建五套主题界面，最后恢复进入烟测前的配置。
     */
    private static void tickThemeCatalog() {
        if (themeSmokeFinished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!opened) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
                return;
            }
            opened = true;
            previousThemeId = SakuraClientState.getActiveThemeId();
            previousSpecialVariant = SakuraClientState.isSpecialThemeVariant();
            themeIndex = 0;
            applyTheme(minecraft, BuiltInThemeCatalog.themeIds().get(themeIndex));
            return;
        }

        if (++themeTicks < 30) {
            return;
        }
        themeTicks = 0;
        themeIndex++;
        if (themeIndex < BuiltInThemeCatalog.themeIds().size()) {
            applyTheme(minecraft, BuiltInThemeCatalog.themeIds().get(themeIndex));
            return;
        }

        ClientConfig.get().display()
                .themeId(previousThemeId)
                .specialVariant(previousSpecialVariant);
        ClientConfig.save();
        ClientEventHandler.loadThemeTexture();
        minecraft.setScreen(new SignInScreen());
        themeSmokeFinished = true;
        LOGGER.info("Sakura theme smoke PASS: {}/{}",
                BuiltInThemeCatalog.themeIds().size(),
                BuiltInThemeCatalog.themeIds().size());
    }

    private static void applyTheme(Minecraft minecraft, String themeId) {
        ClientConfig.get().display().themeId(themeId).specialVariant(false);
        ClientEventHandler.loadThemeTexture();
        minecraft.setScreen(new SignInScreen());
        LOGGER.info("Sakura theme smoke loaded: id={}, texture={}",
                SakuraClientState.getActiveThemeId(), SakuraClientState.getThemeTexture());
    }

    private static Screen inputForm(Screen parent) {
        return new StringInputScreen(
                parent,
                Text.literal("Input form"),
                Text.literal("Enter test content"),
                ".*",
                "Sakura",
                values -> {
                    return new StringList();
                }
        );
    }
}
