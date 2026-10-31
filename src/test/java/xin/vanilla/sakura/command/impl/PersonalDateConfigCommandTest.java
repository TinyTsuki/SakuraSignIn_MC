package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.*;

public class PersonalDateConfigCommandTest {
    private CommandDispatcher<CommandSource> dispatcher() {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(PersonalDateConfigCommand.build());
        return dispatcher;
    }

    private CommandContext<CommandSource> parse(String tail) {
        String input = "personalDate " + tail;
        ParseResults<CommandSource> parsed = dispatcher().parse(input, null);
        assertFalse("Unconsumed command: " + input, parsed.getReader().canRead());
        assertTrue("Parse failure: " + input, parsed.getExceptions().isEmpty());
        assertNotNull("Missing handler: " + input, parsed.getContext().getCommand());
        return parsed.getContext().build(input);
    }

    private void calendar(String input, String expected) {
        CommandContext<CommandSource> context = parse("set birthday 1 " + input + " 10-9");
        assertTrue(context.getArgument("calendar", Object.class) instanceof ResourceLocation);
        assertEquals(expected, ResourceLocationArgument.getId(context, "calendar").toString());
        assertEquals("10-9", StringArgumentType.getString(context, "date"));
        assertEquals(1, IntegerArgumentType.getInteger(context, "slot"));
    }

    @Test public void acceptsGregorianNamespaceAndPreservesFollowingDate() {
        calendar("minecraft:gregorian", "minecraft:gregorian");
    }

    @Test public void acceptsLunarNamespace() {
        calendar("sakura_sign_in:chinese_lunar", "sakura_sign_in:chinese_lunar");
    }

    @Test public void appliesVanillaDefaultNamespace() {
        calendar("gregorian", "minecraft:gregorian");
    }

    @Test public void acceptsResourcePathsWithoutConsumingDateArgument() {
        calendar("example:season/summer", "example:season/summer");
    }

    @Test public void usesSerializableVanillaResourceArgumentAndKeepsSuggestions() {
        CommandNode<CommandSource> node = dispatcher().getRoot().getChild("personalDate")
                .getChild("set").getChild("preset").getChild("slot").getChild("calendar");
        assertTrue(node instanceof ArgumentCommandNode);
        ArgumentCommandNode<CommandSource, ?> argument = (ArgumentCommandNode<CommandSource, ?>) node;
        assertTrue(argument.getType() instanceof ResourceLocationArgument);
        assertNotNull(argument.getCustomSuggestions());
        assertNotNull(node.getChild("date").getCommand());
    }

    @Test public void rejectsMalformedCalendarIds() {
        for (String id : new String[]{"minecraft::gregorian", "Minecraft:gregorian",
                "minecraft:gre$gorian", "minecraft:gregorian\"", "minecraft:gre\\gorian"}) {
            rejected("set birthday 1 " + id + " 10-9");
        }
    }

    @Test public void rejectsMissingAndTrailingArgumentsAndOutOfRangeSlots() {
        for (String tail : new String[]{"set birthday 1", "set birthday 1 minecraft:gregorian",
                "set birthday 1 minecraft:gregorian 10-9 extra", "set birthday 0 minecraft:gregorian 10-9",
                "set birthday 17 minecraft:gregorian 10-9"}) {
            rejected(tail);
        }
    }

    @Test public void preservesListAndClearRoutes() {
        parse("list");
        parse("clear birthday 1");
        parse("clear birthday 16");
    }

    private void rejected(String tail) {
        ParseResults<CommandSource> parsed = dispatcher().parse("personalDate " + tail, null);
        assertTrue("Accepted invalid input: " + tail, parsed.getReader().canRead()
                || !parsed.getExceptions().isEmpty() || parsed.getContext().getCommand() == null);
    }
}
