package xin.vanilla.sakura.network.packet;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.network.data.AdvancementData;

import java.util.Optional;

import static org.junit.Assert.*;

/** Pins the existing MC21 wire; native DisplayInfo getters are available on both sides. */
public class AdvancementDataTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    public void nativeDisplayMetadataKeepsTargetWireOrderAndOptionalBackground() {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        ResourceLocation id = ResourceLocation.parse("sakura_sign_in:test");
        ResourceLocation background = ResourceLocation.parse(
                "minecraft:textures/gui/advancements/backgrounds/stone.png");
        for (boolean present : new boolean[]{false, true}) {
            DisplayInfo display = new DisplayInfo(new ItemStack(Items.DIAMOND, 3),
                    Component.literal("Title"), Component.literal("Description"),
                    present ? Optional.of(background) : Optional.empty(),
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
            assertEquals(Items.DIAMOND, restored.getIcon().getItem());
            assertEquals(3, restored.getIcon().getCount());
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
