package xin.vanilla.sakura.network.packet;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.network.data.AdvancementData;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Pins the existing wire layout while using the current native display type.
 */
public class AdvancementDataTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        xin.vanilla.sakura.test.NativeBootstrap.initialize();
    }

    @Test
    public void absentIconRoundTripsWithoutAnAirTemplate() {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        var id = Identifier.parse("sakura_sign_in:without_display");
        var source = new AdvancementData(id, null);
        assertTrue(source.iconStack().isEmpty());

        var wire = new TestBaniraPacketBuffer();
        source.writeToBuffer(wire);
        wire.readIdentifier();
        assertEquals("minecraft:air", wire.readUtf());
        assertEquals(0, wire.readVarInt());

        var roundTrip = new TestBaniraPacketBuffer();
        source.writeToBuffer(roundTrip);
        assertTrue(AdvancementData.readFromBuffer(roundTrip).iconStack().isEmpty());

        var genuineIcon = new AdvancementData(id,
                AdvancementData.createDisplayInfo("Icon", "", new ItemStack(Items.STRUCTURE_VOID)));
        assertTrue(genuineIcon.iconStack().is(Items.STRUCTURE_VOID));
    }

    @Test
    public void nativeDisplayMetadataKeepsTargetWireOrderAndOptionalBackground() {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        Identifier id = Identifier.parse("sakura_sign_in:test");
        Identifier background = Identifier.parse(
                "minecraft:textures/gui/advancements/backgrounds/stone.png");
        for (boolean present : new boolean[]{false, true}) {
            DisplayInfo display = new DisplayInfo(new ItemStackTemplate(Items.DIAMOND, 3),
                    Component.literal("Title"), Component.literal("Description"),
                    present ? Optional.of(new net.minecraft.core.ClientAsset.ResourceTexture(background)) : Optional.empty(),
                    AdvancementType.CHALLENGE, true, false, true);
            AdvancementData source = new AdvancementData(id, display);
            TestBaniraPacketBuffer wire = new TestBaniraPacketBuffer();
            source.writeToBuffer(wire);
            wire.writeUtf("end");
            assertEquals(id.getPath(), wire.readIdentifier().getPath());
            assertEquals("minecraft:diamond", wire.readUtf());
            assertEquals(3, wire.readVarInt());
            assertEquals("Title", wire.readUtf());
            assertEquals("Description", wire.readUtf());
            assertEquals(present ? background.toString() : "", wire.readUtf());
            assertEquals(AdvancementType.CHALLENGE, wire.readEnum(AdvancementType.class));
            assertTrue(wire.readBoolean());
            assertFalse(wire.readBoolean());
            assertTrue(wire.readBoolean());
            assertEquals("end", wire.readUtf());

            TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
            source.writeToBuffer(buffer);
            buffer.writeUtf("end");
            AdvancementData decoded = AdvancementData.readFromBuffer(buffer);
            assertEquals(id, decoded.getId());
            DisplayInfo restored = decoded.getDisplayInfo();
            assertEquals(Items.DIAMOND, restored.getIcon().typeHolder().value());
            assertEquals(3, restored.getIcon().count());
            assertEquals(display.getBackground(), restored.getBackground());
            assertEquals("Title", restored.getTitle().getString());
            assertEquals("Description", restored.getDescription().getString());
            assertEquals(AdvancementType.CHALLENGE, restored.getType());
            assertTrue(restored.shouldShowToast());
            assertFalse(restored.shouldAnnounceChat());
            assertTrue(restored.isHidden());
            assertEquals("end", buffer.readUtf());
        }
    }
}
