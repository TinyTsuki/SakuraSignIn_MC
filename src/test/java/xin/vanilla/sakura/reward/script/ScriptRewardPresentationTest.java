package xin.vanilla.sakura.reward.script;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Test;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.api.reward.client.*;
import xin.vanilla.sakura.api.reward.script.ScriptRewardDescriptor;
import xin.vanilla.sakura.api.reward.script.ScriptRewardField;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.network.packet.ScriptRewardTypesPacket;
import xin.vanilla.sakura.reward.Reward;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScriptRewardPresentationTest {
    private final RewardTypeId id = RewardTypeId.parse("test:remote");

    @After
    public void clear() {
        SakuraRewardClient.replaceRemoteScripts(Collections.emptyMap());
        SakuraClientPacketHandlers.registerScriptRewards((packet, origin) -> {
        });
    }

    @Test
    public void clientCanRoundTripWithoutRegisteringServerExecutors() {
        RewardPresentation<JsonObject> presentation = new RewardPresentation<JsonObject>() {
            public Component displayName(RewardDisplayContext context, JsonObject value) {
                return null;
            }

            public void renderIcon(RewardRenderContext context, JsonObject value) {
            }
        };
        SakuraRewardClient.replaceRemoteScripts(Collections.singletonMap(id, RewardClientExtension.builder(presentation).build()));
        JsonObject input = new JsonObject();
        input.addProperty("amount", 15);
        Reward reward = SakuraRewardClient.create(input, id, BigDecimal.ONE);
        input.addProperty("amount", 99);
        assertEquals(15, reward.getContent().get("amount").getAsInt());
        JsonObject decoded = SakuraRewardClient.decode(reward);
        decoded.addProperty("amount", 37);
        assertEquals(15, reward.getContent().get("amount").getAsInt());
        assertFalse(SakuraRewards.find(id).isPresent());
        SakuraRewardClient.replaceRemoteScripts(Collections.emptyMap());
        assertFalse(SakuraRewardClient.find(id).isPresent());
    }

    @Test
    public void descriptorCopiesDefaultsAndRejectsOversizedMetadata() {
        JsonObject data = new JsonObject();
        data.addProperty("amount", 8);
        ScriptRewardDescriptor descriptor = new ScriptRewardDescriptor(id, "Coins", "minecraft:gold_nugget", data);
        data.addProperty("amount", 9);
        descriptor.defaults().addProperty("amount", 10);
        assertEquals(8, descriptor.defaults().get("amount").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> new ScriptRewardDescriptor(id, "", "minecraft:paper", data));
        assertThrows(IllegalArgumentException.class, () -> new ScriptRewardDescriptor(id, "Coins", "../../file", data));
    }

    @Test
    public void copiedJsonSeparatesNestedObjectsAndArrays() {
        JsonObject original = new com.google.gson.JsonParser().parse("{\"nested\":{\"amount\":7},\"values\":[{\"amount\":9}]}").getAsJsonObject();
        JsonObject copy = xin.vanilla.sakura.api.reward.script.JsonRewardCodec.copy(original);
        copy.getAsJsonObject("nested").addProperty("amount", 11);
        copy.getAsJsonArray("values").get(0).getAsJsonObject().addProperty("amount", 13);
        assertEquals(7, original.getAsJsonObject("nested").get("amount").getAsInt());
        assertEquals(9, original.getAsJsonArray("values").get(0).getAsJsonObject().get("amount").getAsInt());
        assertNull(xin.vanilla.sakura.api.reward.script.JsonRewardCodec.copy(null));
    }

    private BaniraPacketBuffer buffer(List<Object> values) {
        AtomicInteger index = new AtomicInteger();
        return (BaniraPacketBuffer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BaniraPacketBuffer.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("write")) {
                        values.add(args[0]);
                        return null;
                    }
                    Object value = values.get(index.getAndIncrement());
                    if (method.getName().equals("readUtf") && args != null && ((String) value).length() > (Integer) args[0])
                        throw new IllegalArgumentException("String too long");
                    return value;
                });
    }

    @Test
    public void packetRoundTripsOnlyDeclarativeMetadata() {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("amount", 100);
        ScriptRewardTypesPacket packet = new ScriptRewardTypesPacket(Collections.singletonList(
                new ScriptRewardDescriptor(id, "Coins", "minecraft:paper", defaults,
                        Collections.singletonList(ScriptRewardField.integer("amount", "Coins", 1, Long.MAX_VALUE)))));
        List<Object> values = new ArrayList<>();
        packet.toBytes(buffer(values));
        ScriptRewardTypesPacket decoded = new ScriptRewardTypesPacket(buffer(values));
        assertEquals(id, decoded.descriptors().get(0).id());
        assertEquals("Coins", decoded.descriptors().get(0).name());
        assertEquals("minecraft:paper", decoded.descriptors().get(0).icon());
        assertEquals(defaults, decoded.descriptors().get(0).defaults());
        assertEquals("Coins", decoded.descriptors().get(0).fields().get(0).label());
        assertEquals(ScriptRewardField.Type.INTEGER, decoded.descriptors().get(0).fields().get(0).type());
        assertEquals("9223372036854775807", decoded.descriptors().get(0).fields().get(0).maximum());
        assertEquals("amount", decoded.descriptors().get(0).amountField());
        assertEquals(7, values.size());
    }

    @Test
    public void inboundCountAndDuplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScriptRewardTypesPacket(buffer(Arrays.asList(-1))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptRewardTypesPacket(buffer(Arrays.asList(33))));
        assertThrows(IllegalArgumentException.class, () -> new ScriptRewardTypesPacket(buffer(Arrays.asList(
                2, "test:a", "A", "minecraft:paper", "{}", "[]", "", "test:a"))));
    }

    @Test
    public void clientCannotSendExecutableTypeRegistrationToServer() {
        AtomicInteger deliveries = new AtomicInteger();
        SakuraClientPacketHandlers.registerScriptRewards((packet, origin) -> deliveries.incrementAndGet());
        BaniraNetworkContext server = new BaniraNetworkContext() {
            public void enqueueWork(Runnable action) {
                action.run();
            }

            public void markHandled() {
            }

            public boolean isClientSide() {
                return false;
            }

            public boolean isServerSide() {
                return true;
            }

            public Object sender() {
                return null;
            }
        };
        ScriptRewardTypesPacket.handle(new ScriptRewardTypesPacket(Collections.emptyList()), server);
        assertEquals(0, deliveries.get());
    }
}
