package xin.vanilla.sakura.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ICommandSource;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import org.junit.Test;

import xin.vanilla.sakura.command.impl.DeleteCommand;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class DeleteCommandTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test
    public void requiresPermissionTwoAndDoesNotRequirePlayerEntity() throws Exception {
        CommandNode<CommandSource> node = DeleteCommand.build().build();
        CommandSource console = new CommandSource(ICommandSource.NULL, Vector3d.ZERO, Vector2f.ZERO,
                null, 2, "Console", new StringTextComponent("Console"), null, null);
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
