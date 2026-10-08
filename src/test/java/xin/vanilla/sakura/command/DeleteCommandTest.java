package xin.vanilla.sakura.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import org.junit.Test;

import xin.vanilla.sakura.command.impl.DeleteCommand;
import xin.vanilla.sakura.command.impl.ResetCommand;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class DeleteCommandTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test
    public void requiresPermissionTwoAndDoesNotRequirePlayerEntity() throws Exception {
        CommandNode<CommandSourceStack> node = DeleteCommand.build().build();
        CommandSourceStack console = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, 2, "Console", Component.literal("Console"), null, null);
        assertTrue(node.canUse(console));
        assertFalse(node.canUse(console.withPermission(1)));
        assertNotNull(node.getChild("player").getCommand());
        assertNotNull(node.getChild("player").getChild("date").getCommand());
    }

    @Test
    public void acceptsIsoAndExistingDateSyntax() throws Exception {
        assertEquals(TODAY, parse("2026-10-09"));
        assertEquals(TODAY, parse("2026 10 9"));
        assertEquals(TODAY, parse("~ ~ ~"));
    }

    @Test
    public void claimResetRequiresPermissionTwoAndExplicitPreset() throws Exception {
        CommandNode<CommandSourceStack> node = ResetCommand.build().build();
        CommandSourceStack console = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                null, 2, "Console", Component.literal("Console"), null, null);
        assertTrue(node.canUse(console));
        assertFalse(node.canUse(console.withPermission(1)));
        assertNull(node.getChild("personalDate").getChild("player").getCommand());
        assertNotNull(node.getChild("personalDate").getChild("player").getChild("target").getCommand());
        assertNotNull(node.getChild("personalDate").getChild("player").getChild("preset")
                .getChild("preset").getCommand());
    }

    @Test
    public void rejectsInvalidDatesWithoutNormalizingToAnotherDay() throws Exception {
        for (String input : new String[]{"2026-02-29", "2026 2 30", "2026 13 9", "2026 1 123", "0 10 9", "all"}) {
            try {
                parse(input);
                fail("Accepted invalid date: " + input);
            } catch (CommandSyntaxException expected) {
                assertNotNull(expected.getRawMessage());
            }
        }
    }

    private LocalDate parse(String input) throws Exception {
        return DeletionDateParser.parse(input, TODAY);
    }
}
