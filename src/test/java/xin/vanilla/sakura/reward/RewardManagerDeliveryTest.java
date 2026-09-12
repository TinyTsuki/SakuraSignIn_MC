package xin.vanilla.sakura.reward;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.network.packet.NotificationToClient;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.banira.common.util.PlayerUtils;
import xin.vanilla.banira.platform.BaniraConfigService;
import xin.vanilla.banira.platform.BaniraNetworkService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.*;
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.data.time.SakuraOnlineTime;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.platform.SakuraPlayerDataService;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.*;

/** Exercises the public entry point and real reward/message executors, without a game server. */
public class RewardManagerDeliveryTest {
    private final List<String> events = new ArrayList<>();
    private PlayerSignInData data;
    private CompoundTag saved;
    private TestPlayer player;
    private BaniraPlatform previousPlatform;
    private RewardConfig previousConfig;
    private Object previousDataService;
    private Object previousOnlineTime;
    private boolean failSave;
    private boolean failSync;
    private Date today;
    private final List<NotificationToClient> packets = new ArrayList<>();
    private int packetAttempts;
    private int failPacketAt;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Before
    public void setUp() throws Exception {
        BaniraTestPlatform.install();
        previousPlatform = BaniraPlatforms.get();
        BaniraConfigService config = (BaniraConfigService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraConfigService.class},
                (proxy, method, args) -> null);
        BaniraNetworkService network = (BaniraNetworkService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraNetworkService.class},
                (proxy, method, args) -> {
                    if ("sendToPlayer".equals(method.getName())) {
                        submitPacket((NotificationToClient) args[0]);
                        return null;
                    }
                    return true;
                });
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> {
                    if ("configService".equals(method.getName())) return config;
                    if ("networkService".equals(method.getName())) return network;
                    return method.invoke(previousPlatform, args);
                }));
        previousConfig = RewardConfigManager.getRewardConfig();
        RewardConfigManager.setRewardConfig(new RewardConfig());
        previousDataService = field(SakuraPlayerData.class, "service").get(null);
        previousOnlineTime = field(SakuraOnlineTime.class, "provider").get(null);
        RewardRegistryTestSupport.reset();
        BuiltInRewardTypes.register();
        data = new PlayerSignInData();
        data.setLanguage("en_us");
        data.setSignInCard(2);
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        player = (TestPlayer) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(field(unsafeClass, "theUnsafe").get(null), TestPlayer.class);
        player.events = events;
        field(Player.class, "inventory").set(player, new CountingInventory(player));
        field(ServerPlayer.class, "chatVisibility").set(player, net.minecraft.world.entity.player.ChatVisiblity.FULL);
        RecordingConnection connection = (RecordingConnection) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(field(unsafeClass, "theUnsafe").get(null), RecordingConnection.class);
        connection.owner = player;
        player.connection = connection;
        SakuraOnlineTime.install(ignored -> 0);
        SakuraPlayerData.install((SakuraPlayerDataService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SakuraPlayerDataService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "get": return data;
                        case "save": save(); break;
                        case "sync": sync(); break;
                        case "saveAndSync": save(); sync(); break;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                }));
        today = RewardManager.getCompensateDate(SakuraClock.serverNow());
    }

    @After
    public void tearDown() throws Exception {
        if (player != null) PlayerUtils.removeRemoteClientDataStatus(player);
        if (previousPlatform != null) BaniraPlatforms.install(previousPlatform);
        if (previousConfig != null) RewardConfigManager.setRewardConfig(previousConfig);
        field(SakuraPlayerData.class, "service").set(null, previousDataService);
        field(SakuraOnlineTime.class, "provider").set(null, previousOnlineTime);
        RewardRegistryTestSupport.reset();
    }

    @Test
    public void automaticSignInSavesCompleteStateBeforeSyncAndResults() {
        configureItemAndPersonalDate();
        signIn(ESignInType.SIGN_IN, today, true);
        assertEquals(Arrays.asList("item", "save", "sync", "notify", "notify"), events);
        assertCompleteSaved(today, true, 2);
        assertEquals(3, reload().getSignInCard());
        assertEquals("YEARLY:" + LocalDate.now().getYear(),
                reload().getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test
    public void resultFailureAfterAutomaticSignInDoesNotInterruptRegistrationOrReplay() {
        configureItemAndPersonalDate();
        player.failNotifyAt = 1;
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 2);
        assertEquals(3, reload().getSignInCard());
        data = reload();
        player.failNotifyAt = 0;
        signIn(ESignInType.REWARD, today, true);
        assertEquals(2, inventory().items);
        assertEquals(1, data.getTotalSignInDays());
    }

    @Test
    public void makeUpFailureStillSavesCardDebitAndFullRecord() {
        addItem();
        Date yesterday = DateUtils.addDay(today, -1);
        player.failNotifyAt = 1;
        signIn(ESignInType.RE_SIGN_IN, yesterday, true);
        assertCompleteSaved(yesterday, true, 2);
        assertEquals(1, reload().getSignInCard());
        data = reload();
        player.failNotifyAt = 0;
        signIn(ESignInType.RE_SIGN_IN, yesterday, true);
        assertEquals(2, inventory().items);
        assertEquals(1, data.getSignInCard());
    }

    @Test
    public void independentClaimSavesBeforeResultFailureAndRetryDoesNotGrantAgain() {
        addItem();
        signIn(ESignInType.SIGN_IN, today, false);
        events.clear();
        player.failNotifyAt = player.notifications + 1;
        signIn(ESignInType.REWARD, today, true);
        assertEquals(Arrays.asList("item", "save", "sync", "notify"), events);
        assertCompleteSaved(today, true, 2);
        data = reload();
        player.failNotifyAt = 0;
        signIn(ESignInType.REWARD, today, true);
        assertEquals(2, inventory().items);
    }

    @Test
    public void saveFailureNeverEmitsSuccessOrSync() {
        addItem();
        failSave = true;
        assertThrows(IllegalStateException.class, () -> signIn(ESignInType.SIGN_IN, today, true));
        assertEquals(Arrays.asList("item", "save"), events);
        assertNull(saved);
        assertTrue(data.isSignedOn(today));
        assertTrue(data.isRewardedOn(today));
        assertEquals(1, data.getTotalSignInDays());
    }

    @Test
    public void syncFailureKeepsSavedRecordAndStillAttemptsResultsWithoutReplay() {
        addItem();
        failSync = true;
        signIn(ESignInType.SIGN_IN, today, true);
        assertEquals(Arrays.asList("item", "save", "sync", "notify", "notify"), events);
        assertCompleteSaved(today, true, 2);
        data = reload();
        failSync = false;
        signIn(ESignInType.REWARD, today, true);
        assertEquals(2, inventory().items);
    }

    @Test
    public void failedMessageBodyIsFailedAndAutomaticHistoryContainsOnlySuccessfulItem() {
        addItem();
        Reward message = new Reward(SakuraComponent.get().literal("message-body"), SakuraRewardTypes.MESSAGE);
        RewardConfigManager.getRewardConfig().getBaseRewards().add(message);
        player.failMessageBody = true;
        RewardGrantResult direct = RewardOperations.grant(context(), message);
        assertEquals(RewardGrantStatus.FAILED, direct.getStatus());
        events.clear();
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 2);
        assertEquals(1, reload().getSignInRecords().get(0).getRewardList().size());
        assertEquals(SakuraRewardTypes.ITEM, reload().getSignInRecords().get(0).getRewardList().get(0).getTypeId());
        int bodyAttempts = player.bodyAttempts;
        data = reload();
        signIn(ESignInType.REWARD, today, true);
        assertEquals(bodyAttempts, player.bodyAttempts);
        assertEquals(2, inventory().items);
    }

    @Test
    public void secondResultFailureDoesNotUndoAlreadySubmittedResultOrPersistedState() {
        addItem();
        player.failNotifyAt = 2;
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 2);
        assertEquals(2, player.notifications);
        assertEquals(1, player.delivered.size());
    }

    @Test
    public void independentClaimKeepsExistingFailedDetailSemanticsWithoutReplayingItemOrMessage() {
        addItem();
        RewardConfigManager.getRewardConfig().getBaseRewards().add(
                new Reward(SakuraComponent.get().literal("message-body"), SakuraRewardTypes.MESSAGE));
        signIn(ESignInType.SIGN_IN, today, false);
        player.failMessageBody = true;
        signIn(ESignInType.REWARD, today, true);
        assertCompleteSaved(today, true, 2);
        RewardList details = reload().getSignInRecords().get(0).getRewardList();
        assertEquals(2, details.size());
        for (Reward detail : details) {
            assertTrue(detail.isRewarded());
            assertTrue(detail.isDisabled());
        }
        data = reload();
        int attempts = player.bodyAttempts;
        signIn(ESignInType.REWARD, today, true);
        assertEquals(attempts, player.bodyAttempts);
        assertEquals(2, inventory().items);
    }

    @Test
    public void productionClaimBatches256CompleteDetailsInOrderThroughRealPacketEncoding() {
        List<String> names = configureLargeRewards();
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 256);
        assertEquals(256, reload().getSignInRecords().get(0).getRewardList().size());
        String recordSnbt = reload().getSignInRecords().get(0).writeToNBT().toString();
        int recordBytes = recordSnbt.getBytes(StandardCharsets.UTF_8).length;
        System.out.println("DIAGNOSTIC SignInRecord256 uniquely named item fixture: snbtUtf8Bytes="
                + recordBytes + ", snbtChars=" + recordSnbt.length()
                + ", PlayerMonthSyncPacketDefaultWriteUtfLimit=32767, exceeds=" + (recordBytes > 32767));
        assertTrue("Reward details must span multiple legal packets", packets.size() > 2);
        List<String> received = new ArrayList<>();
        for (NotificationToClient packet : packets) {
            assertEquals("SUCCESS", packet.styleName());
            assertEquals(SakuraNotificationTypes.SIGN_IN, packet.typeId());
            Component component = SakuraComponent.get().deserialize(
                    new JsonParser().parse(packet.componentJson()).getAsJsonObject());
            collectNames(component, received);
        }
        assertEquals(names, received);
        assertTrue(events.indexOf("save") < events.indexOf("packet"));
        assertTrue(events.indexOf("sync") < events.indexOf("packet"));
    }

    @Test
    public void componentBackedItemSurvivesSaveAndCompleteMonthTransfer() {
        ItemStack item = new ItemStack(Items.APPLE, 2);
        item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("component reward")
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        CompoundTag custom = new CompoundTag();
        custom.putString("detail", repeat("x", 40000));
        item.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
        Reward reward = new Reward(item, SakuraRewardTypes.ITEM);
        assertTrue(reward.getContent().has("components"));
        assertFalse(reward.getContent().has("nbt"));
        RewardConfigManager.getRewardConfig().getBaseRewards().add(reward);

        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 2);
        SignInRecord savedRecord = reload().getSignInRecords().get(0);
        assertEquals(reward.getContent(), savedRecord.getRewardList().get(0).getContent());
        String month = xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket.monthOf(savedRecord);
        List<xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket> parts =
                xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket.prepare(
                        player.getUUID(), month, Collections.singletonList(savedRecord));
        assertTrue(parts.size() > 1);
        xin.vanilla.sakura.network.month.MonthDataTransfer.Receiver receiver =
                new xin.vanilla.sakura.network.month.MonthDataTransfer.Receiver(System::nanoTime);
        Optional<List<String>> completed = Optional.empty();
        for (int index = 0; index < parts.size(); index++) {
            xin.vanilla.sakura.network.TestBaniraPacketBuffer buffer =
                    new xin.vanilla.sakura.network.TestBaniraPacketBuffer();
            parts.get(index).toBytes(buffer);
            completed = receiver.accept(new xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket(buffer).getPart());
            assertEquals(index == parts.size() - 1, completed.isPresent());
        }
        SignInRecord received = xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket.decodeRecords(
                month, completed.orElseThrow()).get(0);
        assertEquals(savedRecord.writeToNBT(), received.writeToNBT());
        ItemStack restored = RewardOperations.decode(received.getRewardList().get(0));
        assertEquals(item.getComponentsPatch(), restored.getComponentsPatch());
        assertEquals(2, restored.getCount());
        assertEquals(custom, restored.get(DataComponents.CUSTOM_DATA).copyTag());
    }

    @Test
    public void partialBatchTransportFailureDoesNotResubmitPagesOrRewardsOnRetry() {
        configureLargeRewards();
        failPacketAt = 2;
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 256);
        // One detail page succeeded, the second failed, and the separate sign-in result was attempted.
        assertEquals(3, packetAttempts);
        assertEquals(2, packets.size());
        List<String> received = new ArrayList<>();
        for (NotificationToClient packet : packets) collectNames(SakuraComponent.get().deserialize(
                new JsonParser().parse(packet.componentJson()).getAsJsonObject()), received);
        assertTrue(received.size() > 0 && received.size() < 256);
        data = reload();
        signIn(ESignInType.REWARD, today, true);
        assertEquals(256, inventory().items);
        List<String> afterRetry = new ArrayList<>();
        for (NotificationToClient packet : packets) collectNames(SakuraComponent.get().deserialize(
                new JsonParser().parse(packet.componentJson()).getAsJsonObject()), afterRetry);
        assertEquals(received, afterRetry);
    }

    @Test
    public void oversizedBuiltInMessageIsFailedWithoutAnyPacketAndCannotReplaySuccessfulItem() {
        PlayerUtils.setRemoteClientModInstalled(player, "banira_codex", true);
        addItem();
        Reward message = new Reward(SakuraComponent.get().literal(repeat("oversized", 3000)), SakuraRewardTypes.MESSAGE);
        RewardConfigManager.getRewardConfig().getBaseRewards().add(message);
        assertEquals(RewardGrantStatus.FAILED, RewardOperations.grant(context(), message).getStatus());
        assertTrue(packets.isEmpty());
        signIn(ESignInType.SIGN_IN, today, true);
        assertCompleteSaved(today, true, 2);
        assertEquals(1, reload().getSignInRecords().get(0).getRewardList().size());
        data = reload();
        signIn(ESignInType.REWARD, today, true);
        assertEquals(2, inventory().items);
    }

    @Test
    public void batchMessageAdapterPreservesInputAndRichDetails() {
        PlayerUtils.setRemoteClientModInstalled(player, "banira_codex", true);
        Component prefix = SakuraComponent.get().literal("prefix").languageCode("zh_cn");
        Component entry = SakuraComponent.get().literal("\u5956\u52b1\ud83c\udf38")
                .color(java.awt.Color.GREEN.getRGB())
                .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sakura help"))
                .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.literal("hover detail")))
                .languageCode("zh_cn");
        String originalPrefix = prefix.toJson().toString();
        String originalEntry = entry.toJson().toString();
        SakuraMessages.successBatch(player, prefix, Collections.singletonList(entry), SakuraNotificationTypes.REWARD);
        assertEquals(originalPrefix, prefix.toJson().toString());
        assertEquals(originalEntry, entry.toJson().toString());
        assertEquals(1, packets.size());
        assertEquals(SakuraNotificationTypes.REWARD, packets.get(0).typeId());
        Component decoded = SakuraComponent.get().deserialize(
                new JsonParser().parse(packets.get(0).componentJson()).getAsJsonObject());
        assertEquals("prefix, " + entry.toString(true), decoded.toString(true));
        Component received = findText(decoded, entry.text());
        assertNotNull(received);
        assertEquals(entry.clone().languageCode("en_us").toJson(), received.toJson());
    }

    @Test
    public void batchPreflightRejectsOversizedLaterEntryWithoutSubmittingEarlierEntry() {
        PlayerUtils.setRemoteClientModInstalled(player, "banira_codex", true);
        assertThrows(RuntimeException.class, () -> SakuraMessages.successBatch(player,
                SakuraComponent.get().literal("prefix"), Arrays.asList(
                        SakuraComponent.get().literal("small"),
                        SakuraComponent.get().literal(repeat("large", 5000))), SakuraNotificationTypes.REWARD));
        assertEquals(0, packetAttempts);
        assertTrue(packets.isEmpty());
    }

    private static Component findText(Component component, String text) {
        if (text.equals(component.text())) return component;
        for (Component child : component.getChildren()) {
            Component found = findText(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private List<String> configureLargeRewards() {
        PlayerUtils.setRemoteClientModInstalled(player, "banira_codex", true);
        List<String> names = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            String name = "reward-" + index + "-" + repeat("detail", 35);
            ItemStack item = new ItemStack(Items.APPLE, 1);
            item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
            RewardConfigManager.getRewardConfig().getBaseRewards().add(new Reward(item, SakuraRewardTypes.ITEM));
            names.add(name + "x1");
        }
        return names;
    }

    private static void collectNames(Component component, List<String> names) {
        if (component.text().startsWith("reward-")) {
            assertEquals(java.awt.Color.GREEN.getRGB(), component.color().argb());
            names.add(component.toString(true));
            return;
        }
        for (Component child : component.getChildren()) collectNames(child, names);
    }

    private static String repeat(String text, int count) {
        return String.join("", Collections.nCopies(count, text));
    }

    private void submitPacket(NotificationToClient packet) {
        events.add("packet");
        packetAttempts++;
        FriendlyByteBuf nativeBuffer = new FriendlyByteBuf(Unpooled.buffer());
        BaniraPacketBuffer buffer = (BaniraPacketBuffer) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraPacketBuffer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "writeUtf": nativeBuffer.writeUtf((String) args[0], (Integer) args[1]); return null;
                        case "readUtf": return nativeBuffer.readUtf((Integer) args[0]);
                        case "writeLong": nativeBuffer.writeLong((Long) args[0]); return null;
                        case "readLong": return nativeBuffer.readLong();
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        try {
            packet.toBytes(buffer);
            if (packetAttempts == failPacketAt) throw new IllegalStateException("injected packet transport failure");
            packets.add(new NotificationToClient(buffer));
        } finally {
            nativeBuffer.release();
        }
    }

    private void configureItemAndPersonalDate() {
        addItem();
        LocalDate day = today.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        data.setPersonalDateSlots(Collections.singletonList(new PlayerPersonalDateSlot(
                "birthday", 0, CalendarIds.GREGORIAN, day.getMonthValue(), day.getDayOfMonth(), "")));
        RewardConfigManager.getRewardConfig().setPersonalDatePresets(Collections.singletonList(
                new PersonalDatePreset("birthday", "Birthday", PersonalDateRecurrence.YEARLY,
                        Collections.singletonList(CalendarIds.GREGORIAN), 1, PersonalDateDeliveryMode.SIGN_IN,
                        0, 0, new RewardList(Collections.singletonList(new Reward(1, SakuraRewardTypes.SIGN_IN_CARD))))));
    }

    private void addItem() {
        RewardConfigManager.getRewardConfig().getBaseRewards().add(
                new Reward(new ItemStack(Items.APPLE, 2), SakuraRewardTypes.ITEM));
    }

    private void signIn(ESignInType type, Date date, boolean automatic) {
        RewardManager.signIn(player, new SignInPacket(DateUtils.toString(date), automatic, type));
    }

    private void save() {
        events.add("save");
        if (failSave) throw new IllegalStateException("injected save failure");
        saved = data.serializeNBT().copy();
    }

    private void sync() {
        events.add("sync");
        if (failSync) throw new IllegalStateException("injected sync failure");
    }

    private PlayerSignInData reload() {
        assertNotNull("Complete state must have been saved", saved);
        PlayerSignInData restored = new PlayerSignInData();
        restored.deserializeNBT(saved.copy());
        return restored;
    }

    private void assertCompleteSaved(Date day, boolean rewarded, int items) {
        PlayerSignInData restored = reload();
        assertEquals(1, restored.getTotalSignInDays());
        assertTrue(restored.isSignedOn(day));
        assertEquals(rewarded, restored.isRewardedOn(day));
        assertEquals(1, restored.getSignInRecords().size());
        SignInRecord record = restored.getSignInRecords().get(0);
        assertEquals(rewarded, record.isRewarded());
        assertEquals(DateUtils.toDateInt(day), DateUtils.toDateInt(record.getCompensateTime()));
        assertEquals(items, inventory().items);
    }

    private RewardGrantContext context() {
        return new RewardGrantContext() {
            public ServerPlayer player() { return player; }
            public UUID playerId() { return player.getUUID(); }
            public Date signInDate() { return today; }
            public String sourceId() { return "test"; }
            public void addSignInCards(int amount) { data.plusSignInCard(amount); }
        };
    }

    private CountingInventory inventory() { return (CountingInventory) player.getInventory(); }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CountingInventory extends Inventory {
        private int items;
        private final TestPlayer owner;
        CountingInventory(TestPlayer owner) { super(owner); this.owner = owner; }
        @Override public boolean add(ItemStack stack) {
            owner.events.add("item");
            items += stack.getCount();
            stack.setCount(0);
            return true;
        }
    }

    private static final class TestPlayer extends ServerPlayer {
        private List<String> events;
        private int notifications;
        private int failNotifyAt;
        private boolean failMessageBody;
        private int bodyAttempts;
        private List<String> delivered;

        private TestPlayer() { super(null, null, null, null); }
        @Override public RegistryAccess registryAccess() {
            return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
        @Override public UUID getUUID() { return new UUID(0, 1665); }
        @Override public boolean hasPermissions(int level) { return true; }
        @Override public Collection<MobEffectInstance> getActiveEffects() { return Collections.emptyList(); }
        @Override public boolean isLocalPlayer() { return false; }

        private void submit(ClientboundSystemChatPacket packet) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
            String text;
            try {
                ClientboundSystemChatPacket.STREAM_CODEC.encode(buffer, packet);
                ClientboundSystemChatPacket decoded = ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer);
                assertFalse("Result messages must stay in chat", decoded.overlay());
                assertEquals(packet.overlay(), decoded.overlay());
                assertEquals(net.minecraft.network.chat.Component.Serializer.toJson(packet.content(), registryAccess()),
                        net.minecraft.network.chat.Component.Serializer.toJson(decoded.content(), registryAccess()));
                text = decoded.content().getString();
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
            if (text.contains("message-body")) {
                bodyAttempts++;
                events.add("message");
                if (failMessageBody) throw new IllegalStateException("injected MESSAGE transport failure");
            } else {
                events.add("notify");
                notifications++;
                if (notifications == failNotifyAt) throw new IllegalStateException("injected result transport failure");
            }
            if (delivered == null) delivered = new ArrayList<>();
            delivered.add(text);
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private TestPlayer owner;

        private RecordingConnection() { super(null, null, null, null); }

        @Override public void send(Packet<?> packet) {
            owner.submit((ClientboundSystemChatPacket) packet);
        }

        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            owner.submit((ClientboundSystemChatPacket) packet);
        }
    }
}
