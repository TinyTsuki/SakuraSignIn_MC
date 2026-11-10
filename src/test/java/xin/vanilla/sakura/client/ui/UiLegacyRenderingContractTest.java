package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.Assert.*;

public class UiLegacyRenderingContractTest {
    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void itemAndFallbackIconsPassTheLegacyItemRendererExplicitly() throws Exception {
        int calls = 0;
        for (MethodNode method : read("client/gui/RewardRenderer$NativeRenderContext").methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!call.owner.equals("xin/vanilla/banira/client/gui/widget/ItemWidget") || !call.name.equals("renderItem"))
                    continue;
                assertEquals("(Lnet/minecraft/client/renderer/ItemRenderer;Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IIZ)V", call.desc);
                calls++;
            }
        }
        assertEquals(2, calls);
    }

    @Test
    public void editorBackgroundUsesLegacyBindingAndAllFourTilingSections() throws Exception {
        int bindings = 0, quads = 0;
        for (MethodNode method : read("screen/RewardOptionScreen").methods) {
            if (!method.name.equals("renderBackgroundTexture")) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertNotEquals("setShaderTexture", call.name);
                if (call.owner.equals("net/minecraft/client/renderer/texture/TextureManager") && call.name.equals("bind"))
                    bindings++;
                if (call.owner.equals("net/minecraft/client/renderer/BufferBuilder") && call.name.equals("begin")) {
                    assertEquals("(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V", call.desc);
                    quads++;
                }
            }
        }
        assertEquals(1, bindings);
        assertEquals(4, quads);
    }

    @Test
    public void calendarStatusIconsUseIndependentThemeTexturesWithoutAtlasBinding() throws Exception {
        int draws = 0;
        for (MethodNode method : read("screen/SignInCell").methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertNotEquals("setShaderTexture", call.name);
                if (method.name.equals("renderStatusIcon") && call.owner.equals("xin/vanilla/sakura/client/theme/ThemeTexture") && call.name.equals("draw"))
                    draws++;
            }
        }
        assertEquals(2, draws);
    }
}
