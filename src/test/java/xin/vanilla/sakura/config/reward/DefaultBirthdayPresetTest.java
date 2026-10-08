package xin.vanilla.sakura.config.reward;

import net.minecraft.server.Bootstrap;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.api.reward.RewardRegistryTestSupport;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.*;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class DefaultBirthdayPresetTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); BaniraTestPlatform.install(); }
    @Before public void prepare() { RewardRegistryTestSupport.reset(); BuiltInRewardTypes.register(); }
    @After public void cleanup() { RewardRegistryTestSupport.reset(); }

    @Test public void defaultBirthdayIsAnAnnualOneDateCakeExample() {
        PersonalDatePreset birthday = birthday(RewardConfig.getDefault());
        assertEquals(PersonalDateRecurrence.YEARLY, birthday.getRecurrence());
        assertEquals(Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR), birthday.getCalendarIds());
        assertEquals(1, birthday.getMaxDateSlots());
        assertEquals(PersonalDateDeliveryMode.SIGN_IN, birthday.getDeliveryMode());
        assertEquals(0, birthday.getValidBeforeDays()); assertEquals(0, birthday.getValidAfterDays());
        assertTrue(PersonalDatePresetValidator.validate(birthday).isEmpty());
        assertEquals(1, birthday.getRewards().size());
        assertEquals(SakuraRewardTypes.ITEM, birthday.getRewards().get(0).getTypeId());
        assertEquals("minecraft:cake", birthday.getRewards().get(0).getContent().get("item").getAsString());
        assertEquals(1, birthday.getRewards().get(0).getContent().get("count").getAsInt());
    }

    @Test public void firstCreationPersistsTheDefaultBirthdayExample() throws Exception {
        RewardConfigRepository repository = new RewardConfigRepository(temporary.newFolder().toPath());
        assertEquals(Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR),
                birthday(repository.loadOrCreate(RewardConfig.getDefault())).getCalendarIds());
    }

    @Test public void existingConfigurationIsNotRewrittenOrGivenAnUnrequestedPreset() throws Exception {
        Path directory = temporary.newFolder().toPath();
        RewardConfigRepository repository = new RewardConfigRepository(directory);
        RewardConfig custom = new RewardConfig();
        custom.getPersonalDatePresets().add(new PersonalDatePreset("custom", "Custom", PersonalDateRecurrence.MONTHLY,
                Collections.singletonList(CalendarIds.GREGORIAN), 2, PersonalDateDeliveryMode.ONLINE, 1, 2, new RewardList()));
        repository.save(custom);
        Path file = directory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        byte[] before = Files.readAllBytes(file);
        RewardConfig loaded = repository.loadOrCreate(RewardConfig.getDefault());
        assertEquals(custom.getPersonalDatePresets(), loaded.getPersonalDatePresets());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test public void defaultInstancesDoNotShareMutableBirthdayRewards() {
        PersonalDatePreset first = birthday(RewardConfig.getDefault());
        PersonalDatePreset second = birthday(RewardConfig.getDefault());
        assertNotSame(first, second); assertNotSame(first.getRewards(), second.getRewards());
        first.getRewards().clear(); assertEquals(1, second.getRewards().size());
    }

    private static PersonalDatePreset birthday(RewardConfig config) {
        PersonalDatePreset preset = config.getPersonalDatePresets().stream()
                .filter(value -> value.getId().equals("birthday")).findFirst().orElse(null);
        assertNotNull("Default rewards must include the birthday reference preset", preset);
        return preset;
    }
}
