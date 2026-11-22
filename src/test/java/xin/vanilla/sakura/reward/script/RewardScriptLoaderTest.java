package xin.vanilla.sakura.reward.script;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.script.*;
import xin.vanilla.sakura.reward.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RewardScriptLoaderTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @After
    public void close() {
        RewardScriptLoader.clear();
    }

    private Path folder() throws Exception {
        return temporary.newFolder().toPath();
    }

    private void source(Path dir, String name, String body) throws Exception {
        Files.write(dir.resolve(name + ".java"), body.getBytes(StandardCharsets.UTF_8));
    }

    private String script(String name, String id) {
        return "package xin.vanilla.banira.generated;\n"
                + "import com.google.gson.JsonObject; import java.util.*;"
                + "import xin.vanilla.sakura.api.reward.*; import xin.vanilla.sakura.api.reward.script.*;"
                + "public class " + name + " extends JsonRewardScript {"
                + "public ScriptRewardDescriptor descriptor() { JsonObject j = new JsonObject(); j.addProperty(\"amount\", 7);"
                + "return new ScriptRewardDescriptor(RewardTypeId.parse(\"" + id + "\"), \"Credits\", \"minecraft:gold_nugget\", j); }"
                + "public RewardGrantResult grant(RewardGrantContext c, JsonObject v) {c.addSignInCards(v.get(\"amount\").getAsInt()); return RewardGrantResult.success();}"
                + "public List<RewardViolation> validate(JsonObject v) {"
                + "return v.get(\"amount\").getAsInt() > 0 ? Collections.<RewardViolation>emptyList() : Collections.singletonList(new RewardViolation(\"amount\", \"positive\"));}"
                + "public xin.vanilla.banira.common.data.Component describe(String l, JsonObject v, boolean a) {return null;}"
                + "public RewardAddPermission permission() {return RewardAddPermission.of(2, \"sakura_sign_in:reward.add.test\");} }";
    }

    @Test
    public void compilesRegistersExecutesAndClearsAtSessionEnd() throws Exception {
        Path dir = folder();
        source(dir, "Credits", script("Credits", "test:credits"));
        RewardScriptLoader.load(dir);
        assertEquals(1, RewardScriptLoader.descriptors().size());
        assertEquals("Credits", RewardScriptLoader.descriptors("en_us").get(0).name());
        JsonObject data = RewardScriptLoader.descriptors().get(0).defaults();
        AtomicInteger granted = new AtomicInteger();
        RewardGrantContext context = new RewardGrantContext() {
            public ServerPlayer player() {
                return null;
            }

            public UUID playerId() {
                return new UUID(0, 1);
            }

            public Date signInDate() {
                return new Date(0);
            }

            public String sourceId() {
                return "test";
            }

            public void addSignInCards(int amount) {
                granted.addAndGet(amount);
            }
        };
        Reward reward = new Reward(data, RewardTypeId.parse("test:credits"));
        assertTrue(RewardOperations.grant(context, reward).isSuccess());
        assertEquals(7, granted.get());
        data.addProperty("amount", -1);
        assertEquals(RewardGrantStatus.INVALID_CONTENT, RewardOperations.grant(context, reward).getStatus());
        assertEquals(7, granted.get());
        RewardScriptLoader.clear();
        assertEquals(RewardGrantStatus.TYPE_UNAVAILABLE, RewardOperations.grant(context, reward).getStatus());
    }

    @Test
    public void compilationFailureDoesNotPartiallyReplaceTypes() throws Exception {
        Path dir = folder();
        source(dir, "Good", script("Good", "test:good"));
        RewardScriptLoader.load(dir);
        source(dir, "Broken", "invalid java");
        assertThrows(IllegalStateException.class, () -> RewardScriptLoader.load(dir));
        assertTrue(SakuraRewards.find(RewardTypeId.parse("test:good")).isPresent());
        assertEquals(1, RewardScriptLoader.descriptors().size());
    }

    @Test
    public void duplicateTypesRejectWholeBatch() throws Exception {
        Path dir = folder();
        source(dir, "First", script("First", "test:duplicate"));
        source(dir, "Second", script("Second", "test:duplicate"));
        assertThrows(IllegalStateException.class, () -> RewardScriptLoader.load(dir));
        assertFalse(SakuraRewards.find(RewardTypeId.parse("test:duplicate")).isPresent());
    }

    @Test
    public void constructorsRunOnCallingThread() throws Exception {
        Path dir = folder();
        String body = script("Owner", "test:owner").replace("public class Owner extends JsonRewardScript {",
                "public class Owner extends JsonRewardScript { public Owner() { if (Thread.currentThread().getId() != "
                        + Thread.currentThread().getId() + "L) throw new IllegalStateException(\"wrong thread\"); }");
        source(dir, "Owner", body);
        RewardScriptLoader.load(dir);
        assertTrue(SakuraRewards.find(RewardTypeId.parse("test:owner")).isPresent());
    }

    @Test
    public void missingDirectoryLoadsNoScripts() throws Exception {
        RewardScriptLoader.load(folder().resolve("absent"));
        assertTrue(RewardScriptLoader.descriptors().isEmpty());
    }

    @Test
    public void missingExternalDependencyCannotBreakDescriptionOrGrant() throws Exception {
        Path dir = folder();
        String body = script("Missing", "test:missing")
                .replace("c.addSignInCards(v.get(\"amount\").getAsInt()); return RewardGrantResult.success();",
                        "throw new NoClassDefFoundError(\"external.Api\");")
                .replace("return null;", "throw new NoClassDefFoundError(\"external.Api\");");
        source(dir, "Missing", body);
        RewardScriptLoader.load(dir);
        Reward reward = new Reward(RewardScriptLoader.descriptors().get(0).defaults(), RewardTypeId.parse("test:missing"));
        assertEquals("test:missing", RewardOperations.describe("en_us", reward, true).toString());
        assertEquals(RewardGrantStatus.FAILED, RewardOperations.grant(null, reward).getStatus());
    }

    @Test
    public void oversizedAndInvalidSourceNamesAreRejected() throws Exception {
        Path dir = folder();
        Files.write(dir.resolve("Large.java"), new byte[262145]);
        assertThrows(java.io.IOException.class, () -> RewardScriptLoader.readSources(dir));
        Path invalid = folder();
        source(invalid, "not-a-class", "");
        assertThrows(java.io.IOException.class, () -> RewardScriptLoader.readSources(invalid));
    }

    @Test
    public void scriptDescriptionLocalizesClientMetadataOnTheServer() throws Exception {
        Path dir = folder();
        String body = script("Localized", "test:localized").replace("return null;",
                "return xin.vanilla.sakura.SakuraComponent.get().literal(l.startsWith(\"zh\") ? \"Gold ZH\" : \"Gold EN\");");
        source(dir, "Localized", body);
        RewardScriptLoader.load(dir);
        assertEquals("Gold ZH", RewardScriptLoader.descriptors("zh_cn").get(0).name());
        assertEquals("Gold EN", RewardScriptLoader.descriptors("en_us").get(0).name());
        assertEquals("Credits", RewardScriptLoader.descriptors().get(0).name());
        ScriptRewardLanguageSync sync = new ScriptRewardLanguageSync();
        Object connection = new Object();
        java.util.List<String> receivedNames = new java.util.ArrayList<>();
        java.util.function.Consumer<String> send = language ->
                receivedNames.add(RewardScriptLoader.descriptors(language).get(0).name());
        sync.sync(connection, "en_us", send);
        sync.sync(connection, "zh_cn", send);
        sync.sync(connection, "zh_cn", send);
        assertEquals(java.util.Arrays.asList("Gold EN", "Gold ZH"), receivedNames);
    }

    @Test
    public void ftbExampleCompilesAndRejectsInvalidAmountsWithoutCoreDependency() throws Exception {
        Path dir = folder();
        Files.copy(Paths.get("examples/reward-scripts/FtbMoneyReward.java"), dir.resolve("FtbMoneyReward.java"));
        RewardScriptLoader.load(dir);
        RewardTypeDefinition<?> type = SakuraRewards.require(RewardTypeId.parse("example:ftb_money"));
        @SuppressWarnings("unchecked") RewardValidator<JsonObject> validator = (RewardValidator<JsonObject>) type.getValidator();
        JsonObject data = new JsonObject();
        data.addProperty("amount", 100);
        assertTrue(validator.validate(data).isEmpty());
        for (String value : Arrays.asList("0", "-1", "0.5", "9223372036854775808")) {
            data.add("amount", new com.google.gson.JsonParser().parse(value));
            assertFalse(validator.validate(data).isEmpty());
        }
    }
}
