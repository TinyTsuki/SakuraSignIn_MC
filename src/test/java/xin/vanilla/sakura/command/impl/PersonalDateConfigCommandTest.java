package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import org.junit.Test;

import static org.junit.Assert.*;

public class PersonalDateConfigCommandTest {
    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(PersonalDateConfigCommand.build());
        return dispatcher;
    }

    private CommandContext<CommandSourceStack> parse(String tail) {
        String input = "personalDate " + tail;
        ParseResults<CommandSourceStack> parsed = dispatcher().parse(input, null);
        assertFalse("Unconsumed command: " + input, parsed.getReader().canRead());
        assertTrue("Parse failure: " + input, parsed.getExceptions().isEmpty());
        assertNotNull("Missing handler: " + input, parsed.getContext().getCommand());
        return parsed.getContext().build(input);
    }

    private void calendar(String input, String expected) {
        CommandContext<CommandSourceStack> context = parse("set birthday 1 " + input + " 10-9");
        assertTrue(context.getArgument("calendar", Object.class) instanceof Identifier);
        assertEquals(expected, IdentifierArgument.getId(context, "calendar").toString());
        assertEquals("10-9", StringArgumentType.getString(context, "date"));
        assertEquals(1, IntegerArgumentType.getInteger(context, "slot"));
    }

    @Test
    public void acceptsGregorianNamespaceAndPreservesFollowingDate() {
        calendar("minecraft:gregorian", "minecraft:gregorian");
    }

    @Test
    public void acceptsLunarNamespace() {
        calendar("sakura_sign_in:chinese_lunar", "sakura_sign_in:chinese_lunar");
    }

    @Test
    public void appliesVanillaDefaultNamespace() {
        calendar("gregorian", "minecraft:gregorian");
    }

    @Test
    public void acceptsResourcePathsWithoutConsumingDateArgument() {
        calendar("example:season/summer", "example:season/summer");
    }

    @Test
    public void usesSerializableVanillaResourceArgumentAndKeepsSuggestions() {
        CommandNode<CommandSourceStack> node = dispatcher().getRoot().getChild("personalDate")
                .getChild("set").getChild("preset").getChild("slot").getChild("calendar");
        assertTrue(node instanceof ArgumentCommandNode);
        ArgumentCommandNode<CommandSourceStack, ?> argument = (ArgumentCommandNode<CommandSourceStack, ?>) node;
        assertTrue(argument.getType() instanceof IdentifierArgument);
        assertNotNull(argument.getCustomSuggestions());
        assertNotNull(node.getChild("date").getCommand());
    }

    @Test
    public void rejectsMalformedCalendarIds() {
        for (String id : new String[]{"minecraft::gregorian", "Minecraft:gregorian",
                "minecraft:gre$gorian", "minecraft:gregorian\"", "minecraft:gre\\gorian"}) {
            rejected("set birthday 1 " + id + " 10-9");
        }
    }

    @Test
    public void rejectsMissingAndTrailingArgumentsAndOutOfRangeSlots() {
        for (String tail : new String[]{"set birthday 1", "set birthday 1 minecraft:gregorian",
                "set birthday 1 minecraft:gregorian 10-9 extra", "set birthday 0 minecraft:gregorian 10-9",
                "set birthday 17 minecraft:gregorian 10-9"}) {
            rejected(tail);
        }
    }

    @Test
    public void preservesListAndClearRoutes() {
        parse("list");
        parse("clear birthday 1");
        parse("clear birthday 16");
    }

    private void rejected(String tail) {
        ParseResults<CommandSourceStack> parsed = dispatcher().parse("personalDate " + tail, null);
        assertTrue("Accepted invalid input: " + tail, parsed.getReader().canRead()
                || !parsed.getExceptions().isEmpty() || parsed.getContext().getCommand() == null);
    }
}
