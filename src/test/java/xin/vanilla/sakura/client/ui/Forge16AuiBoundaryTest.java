package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.Assert.*;

public class Forge16AuiBoundaryTest {
    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (java.io.InputStream input = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void themePackUsesTheForge16ResourceContract() throws Exception {
        int resources = 0;
        for (lombok.var method : read("client/theme/BuiltInThemeCatalog").methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode  && ((MethodInsnNode) instruction).name.equals("getResource")) {
                    assertEquals("(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/resources/IResource;", ((MethodInsnNode) instruction).desc);
                    resources++;
                }
        assertEquals(1, resources);
    }

    @Test
    public void commandRewardKeepsTheForge16SourceAndCommandEntry() throws Exception {
        int commands = 0;
        for (lombok.var method : read("reward/builtin/BuiltInRewardTypes").methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode  && ((MethodInsnNode) instruction).name.equals("performCommand")) {
                    assertEquals("(Lnet/minecraft/command/CommandSource;Ljava/lang/String;)I", ((MethodInsnNode) instruction).desc);
                    commands++;
                }
        assertEquals(1, commands);
    }
}
