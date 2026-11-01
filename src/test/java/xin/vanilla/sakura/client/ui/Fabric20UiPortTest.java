package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Fabric20UiPortTest {
    private ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }

    private long calls(ClassNode node, String owner, String name) {
        return node.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.endsWith(owner) && call.name.equals(name)).count();
    }

    @Test
    public void fivePageOwnersAndSharedSessionsContainNoForgeOrVendorCalls() throws Exception {
        for (String page : new String[]{"SignInScreen", "PersonalDateConfigScreen", "RewardOptionScreen", "LotteryScreen", "LotteryRevealScreen"}) {
            ClassNode type = node("screen/" + page);
            assertTrue(page, type.interfaces.contains("xin/vanilla/banira/api/client/ui/UiPageSource"));
            for (var method : type.methods)
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call)
                        assertFalse(call.owner, call.owner.startsWith("net/minecraftforge/")
                                || call.owner.startsWith("net/neoforged/") || call.owner.startsWith("com/sighs/"));
        }
        for (String session : new String[]{"SignInUiSession", "PersonalDateUiSession", "RewardEditorUiSession", "LotteryUiSession", "LotteryRevealUiSession"})
            for (var method : node("client/ui/" + session).methods)
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call) {
                        assertFalse(call.owner, call.owner.contains("SakuraNetwork") || call.owner.contains("RewardManager")
                                || call.owner.contains("CostPayment") || call.owner.startsWith("org/lwjgl/"));
                    }
    }

    @Test
    public void fabricInventoryBindingUsesVanillaMatchingAndSavedKeyNames() throws Exception {
        assertTrue(calls(node("screen/LotteryRevealScreen"), "client/KeyMapping", "matches") > 0);
        assertTrue(calls(node("screen/SignInScreen"), "client/KeyMapping", "matches") > 0);
        for (String page : new String[]{"LotteryRevealScreen", "SignInScreen"}) {
            ClassNode type = node("screen/" + page);
            assertTrue(calls(type, "client/KeyMapping", "saveString") > 0);
            assertEquals(0, calls(type, "client/KeyMapping", "getKey"));
        }
        String quick = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/gui/SakuraQuickActions.java"));
        assertTrue(quick.contains("Minecraft.getInstance().options == null"));
        String events = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/event/ClientEventHandler.java"));
        String tick = events.substring(events.indexOf("public static void onClientTick"));
        assertTrue(tick.contains("SakuraQuickActions.register()"));
    }

    @Test
    public void clientRefreshAndThemeUploadUseExistingLifecycle() throws Exception {
        assertEquals(1, calls(node("network/ClientProxy"), "client/ui/SakuraUiPages", "signInChanged"));
        ClassNode events = node("event/ClientEventHandler");
        assertEquals(1, calls(events, "client/theme/SakuraThemeTextures", "install"));
        assertTrue(calls(events, "client/Minecraft", "isSameThread") > 0);
        assertTrue(calls(events, "client/Minecraft", "execute") > 0);
        String bootstrap = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/internal/fabric/client/FabricSakuraClientEntrypoint.java"));
        assertTrue(bootstrap.contains("SakuraClientBootstrap.init()"));
    }

    @Test
    public void isolatedPublicationUsesRemappedSourcesAndDoesNotTouchClientOptions() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("id 'maven-publish'"));
        assertTrue(build.contains("destinationDirectory.set(baniraDistributionDirectory)"));
        assertTrue(build.contains("artifact tasks.named('remapSourcesJar')"));
        assertTrue(build.contains("if (providers.gradleProperty('banira.validationOutputRoot').orNull == null) prepareBaniraDevFpsOverlay()"));
        String isolation = Files.readString(Path.of("gradle/aui-validation-output.gradle"));
        assertTrue(isolation.contains("disallowChanges()"));
        assertTrue(isolation.contains("Publication escaped validation output root"));
    }
}
