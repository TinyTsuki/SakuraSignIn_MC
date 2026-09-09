package xin.vanilla.sakura.network.packet;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.mixin.DisplayInfoAccessor;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.network.data.AdvancementData;

import static org.junit.Assert.*;

public class AdvancementDataTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    public void dedicatedServerEncodingDoesNotCallClientOnlyGetters() {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        ItemStack icon = new ItemStack(Items.DIAMOND, 3);
        icon.getOrCreateTag().putString("marker", "preserved");
        ResourceLocation background = new ResourceLocation("minecraft:textures/gui/advancements/backgrounds/stone.png");
        DisplayInfo display = new ServerDisplayInfo(icon, background);
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        ResourceLocation id = new ResourceLocation("sakura_sign_in:test");
        new AdvancementData(id, display).writeToBuffer(buffer);
        AdvancementData decoded = AdvancementData.readFromBuffer(buffer);
        assertEquals(id, decoded.getId());
        DisplayInfo restored = decoded.getDisplayInfo();
        assertEquals(icon.save(new net.minecraft.nbt.CompoundTag()), restored.getIcon().save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(background, restored.getBackground());
        assertEquals("Title", restored.getTitle().getString());
        assertEquals("Description", restored.getDescription().getString());
        assertEquals(FrameType.CHALLENGE, restored.getFrame());
        assertTrue(restored.shouldShowToast());
        assertTrue(restored.shouldAnnounceChat());
        assertTrue(restored.isHidden());
    }

    private static final class ServerDisplayInfo extends DisplayInfo implements DisplayInfoAccessor {
        private final ItemStack icon;
        private final ResourceLocation background;

        private ServerDisplayInfo(ItemStack icon, ResourceLocation background) {
            super(icon, Component.literal("Title"), Component.literal("Description"), background,
                    FrameType.CHALLENGE, true, true, true);
            this.icon = icon;
            this.background = background;
        }

        @Override public ItemStack sakura$getIcon() { return icon; }
        @Override public ResourceLocation sakura$getBackground() { return background; }
        @Override public boolean sakura$shouldShowToast() { return true; }
        @Override public ItemStack getIcon() { throw new AssertionError("Client-only getIcon"); }
        @Override public ResourceLocation getBackground() { throw new AssertionError("Client-only getBackground"); }
        @Override public boolean shouldShowToast() { throw new AssertionError("Client-only shouldShowToast"); }
    }
}
