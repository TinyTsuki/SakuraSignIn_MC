package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Fabric16UiPortTest {
    private ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (lombok.var input = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }

    private long calls(ClassNode node, String owner, String name) {
        return node.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith(owner) && ((MethodInsnNode) instruction).name.equals(name)).count();
    }

    @Test
    public void fivePageOwnersAndSharedSessionsContainNoForgeOrVendorCalls() throws Exception {
        for (String page : new String[]{"SignInScreen", "PersonalDateConfigScreen", "RewardOptionScreen", "LotteryScreen", "LotteryRevealScreen"}) {
            ClassNode type = node("screen/" + page);
            assertTrue(page, type.interfaces.contains("xin/vanilla/banira/api/client/ui/UiPageSource"));
            for (lombok.var method : type.methods)
                for (lombok.var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode)
                        assertFalse(((MethodInsnNode) instruction).owner, ((MethodInsnNode) instruction).owner.startsWith("net/minecraftforge/")
                                || ((MethodInsnNode) instruction).owner.startsWith("net/neoforged/") || ((MethodInsnNode) instruction).owner.startsWith("com/sighs/"));
        }
        for (String session : new String[]{"SignInUiSession", "PersonalDateUiSession", "RewardEditorUiSession", "LotteryUiSession", "LotteryRevealUiSession"})
            for (lombok.var method : node("client/ui/" + session).methods)
                for (lombok.var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode) {
                        assertFalse(((MethodInsnNode) instruction).owner, ((MethodInsnNode) instruction).owner.contains("SakuraNetwork") || ((MethodInsnNode) instruction).owner.contains("RewardManager")
                                || ((MethodInsnNode) instruction).owner.contains("CostPayment") || ((MethodInsnNode) instruction).owner.startsWith("org/lwjgl/"));
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
        String quick = xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("src/main/java/xin/vanilla/sakura/client/gui/SakuraQuickActions.java"));
        assertTrue(quick.contains("Minecraft.getInstance().options == null"));
        String events = xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("src/main/java/xin/vanilla/sakura/event/ClientEventHandler.java"));
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
        String bootstrap = xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("src/main/java/xin/vanilla/sakura/internal/fabric/client/FabricSakuraClientEntrypoint.java"));
        assertTrue(bootstrap.contains("SakuraClientBootstrap.init()"));
    }

    @Test
    public void isolatedPublicationUsesRemappedSourcesAndDoesNotTouchClientOptions() throws Exception {
        String build = xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("build.gradle"));
        assertTrue(build.contains("id 'maven-publish'"));
        assertTrue(build.contains("destinationDirectory.set(baniraDistributionDirectory)"));
        assertTrue(build.contains("artifact tasks.named('remapSourcesJar')"));
        assertTrue(build.contains("tasks.matching { it.name == 'runClient' }.configureEach"));
        assertTrue(build.contains("doFirst { prepareBaniraDevFpsOverlay() }"));
        assertFalse(build.contains("orNull == null) prepareBaniraDevFpsOverlay()"));
        String isolation = xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("gradle/aui-validation-output.gradle"));
        assertTrue(isolation.contains("disallowChanges()"));
        assertTrue(isolation.contains("Publication escaped validation output root"));
    }

    @Test
    public void nativePagesKeepPoseStackDrawingAndConnectionGuards() throws Exception {
        for (String page : new String[]{"SignInScreen", "PersonalDateConfigScreen", "RewardOptionScreen", "LotteryScreen", "LotteryRevealScreen"}) {
            ClassNode type = node("screen/" + page);
            assertTrue(page, nativeDrawing(type));
            assertTrue(page, calls(type, "client/Minecraft", "getConnection") > 0);
            for (lombok.var method : type.methods) {
                assertFalse(method.desc, method.desc.contains("GuiGraphics"));
                for (lombok.var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode)
                        assertFalse(((MethodInsnNode) instruction).owner, ((MethodInsnNode) instruction).owner.contains("GuiGraphics") || ((MethodInsnNode) instruction).owner.startsWith("org/joml/"));
            }
        }
        ClassNode renderer = node("client/gui/RewardRenderer");
        assertTrue(renderer.methods.stream().anyMatch(method -> method.name.equals("renderCustomReward")
                && method.desc.startsWith("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/ItemRenderer;")));
        assertEquals("xin/vanilla/banira/client/gui/PlayerConfigScreen", node("screen/PersonalDateConfigScreen").superName);
        assertTrue(node("screen/PersonalDateConfigScreen$EntryRowWidget").methods.stream().anyMatch(method -> method.name.equals("render")
                && method.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")));
    }

    private boolean nativeDrawing(ClassNode type) throws Exception {
        if (type.methods.stream().anyMatch(method -> method.name.equals("onRender")
                && method.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))) return true;
        if (type.superName == null || !type.superName.startsWith("xin/vanilla/banira/client/gui/")) return false;
        ClassNode parent = new ClassNode();
        try (lombok.var input = getClass().getResourceAsStream("/" + type.superName + ".class")) {
            assertNotNull(type.superName, input);
            new ClassReader(input).accept(parent, 0);
        }
        return nativeDrawing(parent);
    }

    @Test
    public void lotteryOwnersResolveNativeRenderersOnlyWhenDrawing() throws Exception {
        for (String page : new String[]{"LotteryScreen", "LotteryRevealScreen"}) {
            ClassNode type = node("screen/" + page);
            assertTrue(page, calls(type, "client/Minecraft", "getItemRenderer") > 0);
            assertFalse(page, type.methods.stream().filter(method -> method.name.equals("<init>"))
                    .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                    .anyMatch(instruction -> instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("getItemRenderer")));
        }
    }
}
