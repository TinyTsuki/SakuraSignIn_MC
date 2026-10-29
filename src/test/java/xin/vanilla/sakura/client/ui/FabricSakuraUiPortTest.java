package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class FabricSakuraUiPortTest {
    @Test
    public void fabricEntryAndInputContractsRemainLoaderNative() throws Exception {
        String entry = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/internal/fabric/client/FabricSakuraClientEntrypoint.java"));
        assertTrue(entry.contains("implements ClientModInitializer"));
        assertEquals(1, entry.split("SakuraClientBootstrap.init\\(", -1).length - 1);
        for (String screen : new String[]{"SignInScreen", "LotteryRevealScreen"}) {
            String code = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/" + screen + ".java"));
            assertTrue(screen, code.contains("keyInventory.matches("));
            assertFalse(screen, code.contains("keyInventory.getKey("));
        }
        String editor = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/RewardOptionScreen.java"));
        assertTrue(editor.contains("BaniraInput.isKeyDown("));
        assertFalse(editor.contains("inputState.isKeyPressed("));
        String smoke = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/internal/client/dev/SakuraNetworkSmokeScreens.java"));
        assertTrue(smoke.contains("runDevRenderAt("));
        assertFalse(smoke.contains("getDeclaredMethod(\"handleDrawScreenPre\""));
    }
    @Test
    public void allFiveOwnersUseThePublicSharedBoundary() throws Exception {
        for (String screen : new String[]{"SignInScreen", "PersonalDateConfigScreen", "RewardOptionScreen", "LotteryScreen", "LotteryRevealScreen"}) {
            String code = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/" + screen + ".java"));
            assertTrue(screen, code.contains("implements UiPageSource"));
            assertTrue(screen, code.contains("BaniraUi.screenRemoved(this)"));
            assertFalse(screen, code.contains("com.sighs."));
        }
    }

    @Test
    public void copiedRenderersUsePublicMinecraft21Contracts() throws Exception {
        int background = 0, factories = 0;
        for (String name : new String[]{"screen/SignInScreen", "client/ui/LotteryUiSession", "client/ui/RewardEditorUiSession",
                "client/theme/BuiltInThemeCatalog", "client/theme/ThemeTexture"}) {
            var node = new ClassNode();
            try (var in = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
                assertNotNull(name, in);
                new ClassReader(in).accept(node, 0);
            }
            for (var method : node.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner, call.owner.startsWith("net/minecraftforge/") || call.owner.startsWith("com/sighs/"));
                    if (call.name.equals("renderBackground")) {
                        assertEquals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", call.desc);
                        background++;
                    }
                    if (call.owner.equals("net/minecraft/resources/ResourceLocation")) {
                        assertNotEquals("<init>", call.name);
                        if (call.name.equals("parse") || call.name.equals("fromNamespaceAndPath")) factories++;
                    }
                }
        }
        assertEquals(1, background);
        assertTrue(factories >= 6);
    }

    @Test
    public void legacyAtlasesStayOutOfProductionWhileTestGoldensRemain() {
        for (String id : new String[]{"sakura", "original", "clover", "maple", "chaos"}) {
            assertFalse(id, Files.exists(Path.of("src/main/resources/assets/sakura_sign_in/textures/gui/sign_in_calendar_" + id + ".png")));
            assertTrue(id, Files.isRegularFile(Path.of("src/test/resources/theme-migration/" + id + ".png")));
        }
    }
}
