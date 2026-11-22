package xin.vanilla.sakura.network.packet;

import com.google.gson.JsonParser;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.script.ScriptRewardDescriptor;
import xin.vanilla.sakura.api.reward.script.ScriptRewardField;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;

import java.util.*;

public final class ScriptRewardTypesPacket implements INetworkPacket {
    private final List<ScriptRewardDescriptor> descriptors;

    public ScriptRewardTypesPacket(List<ScriptRewardDescriptor> descriptors) {
        if (descriptors.size() > 32) throw new IllegalArgumentException("Too many script reward types");
        this.descriptors = Collections.unmodifiableList(new ArrayList<>(descriptors));
    }

    public ScriptRewardTypesPacket(BaniraPacketBuffer buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 32) throw new IllegalArgumentException("Invalid script reward type count");
        List<ScriptRewardDescriptor> result = new ArrayList<>();
        Set<RewardTypeId> ids = new HashSet<>();
        for (int i = 0; i < size; i++) {
            RewardTypeId id = RewardTypeId.parse(buffer.readUtf(256));
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate script reward type");
            String name = buffer.readUtf(128);
            String icon = buffer.readUtf(256);
            JsonObject defaults = new JsonParser().parse(buffer.readUtf(4096)).getAsJsonObject();
            JsonArray schema = new JsonParser().parse(buffer.readUtf(8192)).getAsJsonArray();
            if (schema.size() > 32) throw new IllegalArgumentException("Too many script fields");
            List<ScriptRewardField> fields = new ArrayList<>();
            schema.forEach(field -> fields.add(ScriptRewardField.fromJson(field.getAsJsonObject())));
            result.add(new ScriptRewardDescriptor(id, name, icon, defaults, fields).withAmountField(buffer.readUtf(128)));
        }
        descriptors = Collections.unmodifiableList(result);
    }

    public List<ScriptRewardDescriptor> descriptors() {
        return descriptors;
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeVarInt(descriptors.size());
        for (ScriptRewardDescriptor descriptor : descriptors) {
            buffer.writeUtf(descriptor.id().toString(), 256);
            buffer.writeUtf(descriptor.name(), 128);
            buffer.writeUtf(descriptor.icon(), 256);
            buffer.writeUtf(descriptor.defaults().toString(), 4096);
            buffer.writeUtf(descriptor.fieldsJson().toString(), 8192);
            buffer.writeUtf(descriptor.amountField(), 128);
        }
    }

    public static void handle(ScriptRewardTypesPacket packet, BaniraNetworkContext context) {
        if (context.isClientSide()) {
            Object origin = context.connectionKey();
            if (origin != null) context.enqueueWork(() -> SakuraClientPacketHandlers.handle(packet, origin));
        }
        context.markHandled();
    }
}
