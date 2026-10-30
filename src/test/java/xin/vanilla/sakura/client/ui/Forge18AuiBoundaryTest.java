package xin.vanilla.sakura.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.Assert.*;

public class Forge18AuiBoundaryTest {
    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/sakura/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void themePackUsesTheForge18ResourceContract() throws Exception {
        int resources = 0;
        for (var method : read("client/theme/BuiltInThemeCatalog").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals("getResource")) {
                    assertEquals("(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;", call.desc);
                    resources++;
                }
        assertEquals(1, resources);
    }

    @Test
    public void commandRewardKeepsTheForge18SourceAndCommandEntry() throws Exception {
        int commands = 0;
        for (var method : read("reward/builtin/BuiltInRewardTypes").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals("performCommand")) {
                    assertEquals("(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I", call.desc);
                    commands++;
                }
        assertEquals(1, commands);
    }
}
