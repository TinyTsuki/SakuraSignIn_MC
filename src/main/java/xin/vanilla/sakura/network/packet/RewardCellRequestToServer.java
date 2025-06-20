package xin.vanilla.sakura.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.enums.EnumSignInStatus;
import xin.vanilla.sakura.network.data.RewardCellSyncData;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 通知服务器将对应月份的签到奖励数据同步至客户端
 */
public class RewardCellRequestToServer {
    private final int year;
    private final int month;

    public RewardCellRequestToServer(int year, int month) {
        this.year = year;
        this.month = month;
    }

    public RewardCellRequestToServer(PacketBuffer buf) {
        this.year = buf.readInt();
        this.month = buf.readInt();
    }

    public RewardCellRequestToServer(Date date) {
        this.year = DateUtils.getYearPart(date);
        this.month = DateUtils.getMonthOfDate(date);
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.year);
        buf.writeInt(this.month);
    }

    public static void handle(RewardCellRequestToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                IPlayerSignInData playerData = PlayerSignInDataCapability.getData(player);
                Date serverDate = DateUtils.getServerDate();
                boolean hasPermissions = player.hasPermissions(ServerConfig.PERMISSION_REWARD_DETAIL.get());
                int pastOffset = ServerConfig.REWARD_VIEW_PAST_RANGE.get();
                int futureOffset = ServerConfig.REWARD_VIEW_FUTURE_RANGE.get();
                List<RewardCellSyncData> dataList = RewardManager.getMonthRewardList(DateUtils.getDate(packet.year, packet.month, 1)
                                , playerData, 0, 0
                        )
                        .entrySet().stream()
                        .map(entry -> entry.getValue().stream()
                                .map(reward -> {
                                    KeyValue<String, EnumSignInStatus> entryKey = entry.getKey();
                                    int cellDateInt = DateUtils.toDateInt(DateUtils.format(entryKey.getKey()));
                                    if (EnumSignInStatus.SIGNED_IN.equals(entry.getKey().getValue())
                                            || EnumSignInStatus.REWARDED.equals(entry.getKey().getValue())
                                            || (hasPermissions
                                            && DateUtils.toDateInt(DateUtils.addDay(serverDate, futureOffset)) >= cellDateInt
                                            && DateUtils.toDateInt(DateUtils.addDay(serverDate, -pastOffset)) <= cellDateInt)
                                    ) {
                                        return new RewardCellSyncData(entryKey.getValue(), entryKey.getKey(), reward);
                                    } else {
                                        return new RewardCellSyncData(entryKey.getValue(), entryKey.getKey(), new Reward());
                                    }
                                })
                                .collect(Collectors.toList())
                        )
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                for (RewardCellSyncToClient synPacket : new RewardCellSyncToClient(dataList).split()) {
                    SakuraUtils.sendPacketToPlayer(synPacket, player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
