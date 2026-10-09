package xin.vanilla.sakura.reward;

import com.google.gson.JsonObject;
import net.minecraft.server.Bootstrap;
import org.junit.*;
import xin.vanilla.sakura.api.reward.*;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RewardMergePreparationTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }
    @Before public void prepare() { RewardRegistryTestSupport.reset(); BuiltInRewardTypes.register(); }
    @After public void cleanup() { RewardRegistryTestSupport.reset(); }

    @Test public void distinctTaggedItemsDoNotDecodeEveryComparedPair() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) bean;
        Assume.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        RewardList input = new RewardList();
        for (int i = 0; i < 128; i++) input.add(item(i, 1));
        RewardManager.mergeRewards(input);
        long thread = Thread.currentThread().getId(), before = allocation.getThreadAllocatedBytes(thread);
        RewardList result = RewardManager.mergeRewards(input);
        long bytes = allocation.getThreadAllocatedBytes(thread) - before;
        assertEquals(128, result.size());
        assertTrue("Per-call prepared merge must avoid quadratic NBT decoding; bytes=" + bytes, bytes < 8L * 1024 * 1024);
        for (int i = 0; i < input.size(); i++) assertSame(input.get(i), result.get(i));
    }

    @Test public void nativeResultsMatchOrderedLegacyFoldForMetadataAndNbt() {
        Random random = new Random(72136);
        for (int run = 0; run < 30; run++) {
            RewardList input = new RewardList();
            for (int i = 0; i < 64; i++) {
                Reward reward;
                switch (random.nextInt(3)) {
                    case 0: reward = item(random.nextInt(8), random.nextInt(4) + 1); break;
                    case 1: reward = number(random.nextInt(5) + 1); break;
                    default:
                        JsonObject value = new JsonObject(); value.addProperty("effect", "minecraft:luck");
                        value.addProperty("duration", random.nextInt(40) + 1); value.addProperty("amplifier", random.nextInt(3));
                        reward = new Reward(value, SakuraRewardTypes.EFFECT);
                }
                reward.setProbability(new BigDecimal(random.nextBoolean() ? "0.5" : "0.50"));
                reward.setRewarded(random.nextBoolean()).setDisabled(random.nextBoolean());
                input.add(reward);
            }
            String before = input.toJsonArray().toString();
            assertEquals(legacy(input).toJsonArray(), RewardManager.mergeRewards(input).toJsonArray());
            assertEquals(before, input.toJsonArray().toString());
        }
    }

    @Test public void equivalentProbabilityScalesMergeButDifferentFlagsDoNot() {
        Reward a = item(0, 2).setProbability(new BigDecimal("0.5"));
        Reward b = item(0, 3).setProbability(new BigDecimal("0.500"));
        Reward c = item(0, 4).setProbability(new BigDecimal("0.5")).setDisabled(true);
        Reward d = item(0, 5).setProbability(new BigDecimal("0.50")).setRewarded(true);
        RewardList result = RewardManager.mergeRewards(new RewardList(Arrays.asList(a, b, c, d)));
        assertEquals(3, result.size());
        assertEquals(5, result.get(0).getContent().get("count").getAsInt());
        assertEquals("0.5", result.get(0).getProbability().toPlainString());
        assertSame(c, result.get(1)); assertSame(d, result.get(2));
    }

    @Test public void refusedOverflowKeepsEarlierSlotAndContinuesScanning() {
        RewardList input = new RewardList(Arrays.asList(number(Integer.MAX_VALUE), number(1), number(2)));
        RewardList result = RewardManager.mergeRewards(input);
        assertEquals(legacy(input).toJsonArray(), result.toJsonArray());
        assertEquals(2, result.size());
        assertEquals(Integer.MAX_VALUE, result.get(0).getContent().get("expPoint").getAsInt());
        assertEquals(3, result.get(1).getContent().get("expPoint").getAsInt());
    }

    @Test public void nullUnknownAndMalformedContentKeepLegacyBehavior() {
        JsonObject unknown = new JsonObject(); unknown.addProperty("data", 7);
        Reward malformed = new Reward(new JsonObject(), SakuraRewardTypes.ITEM);
        RewardList input = new RewardList(Arrays.asList(null, item(1, 1), malformed,
                new Reward(unknown, RewardTypeId.parse("missing:reward")), item(1, 2)));
        RewardList expected = legacy(input), result = RewardManager.mergeRewards(input);
        assertEquals(expected.size(), result.size()); assertNull(result.get(0));
        assertEquals(expected.toJsonArray(), result.toJsonArray());
        assertSame(malformed, result.get(2));
    }

    @Test public void extensionCanDeclineEqualKeysAndChangeKeysAfterMerge() {
        for (boolean changes : new boolean[]{false, true}) {
            RewardTypeId id = extension(changes ? "changing" : "declining", changes, null);
            RewardList input = new RewardList();
            for (int i = 0; i < 32; i++) { JsonObject value = new JsonObject(); value.addProperty("value", 1); input.add(new Reward(value, id)); }
            String before = input.toJsonArray().toString();
            assertEquals(legacy(input).toJsonArray(), RewardManager.mergeRewards(input).toJsonArray());
            assertEquals(before, input.toJsonArray().toString());
        }
    }

    @Test public void mixedExtensionSideEffectsCannotMakeBuiltinPreparationStale() {
        RewardList input = new RewardList(); input.add(item(1, 1));
        AtomicInteger callbacks = new AtomicInteger();
        RewardTypeId id = extension("mutating", false, () -> {
            callbacks.incrementAndGet();
            input.get(0).setContent(item(9, 1).getContent());
        });
        JsonObject value = new JsonObject(); value.addProperty("value", 1);
        input.add(new Reward(value, id)); input.add(new Reward(new com.google.gson.Gson().fromJson(value, JsonObject.class), id)); input.add(item(9, 2));
        RewardList result = RewardManager.mergeRewards(input);
        assertTrue(callbacks.get() > 0);
        assertEquals(2, result.size()); assertEquals(3, result.get(0).getContent().get("count").getAsInt());
    }

    @Test public void laterCallsObserveChangedPayloadInsteadOfCachedPriorData() {
        RewardList input = new RewardList(Arrays.asList(item(1, 1), item(1, 2)));
        assertEquals(1, RewardManager.mergeRewards(input).size());
        input.get(1).setContent(item(2, 2).getContent());
        assertEquals(2, RewardManager.mergeRewards(input).size());
    }

    @Test public void overriddenBuiltinCodecStillUsesOriginalDecoderCallOrder() {
        List<RewardTypeDefinition<?>> definitions = SakuraRewards.all();
        RewardTypeDefinition<?> original = SakuraRewards.require(SakuraRewardTypes.ITEM);
        RewardRegistryTestSupport.reset();
        AtomicInteger decodes = new AtomicInteger();
        xin.vanilla.sakura.reward.builtin.ItemRewardCodec delegate = new xin.vanilla.sakura.reward.builtin.ItemRewardCodec();
        RewardCodec<net.minecraft.world.item.ItemStack> codec = new RewardCodec<net.minecraft.world.item.ItemStack>() {
            @Override public net.minecraft.world.item.ItemStack decode(JsonObject value) throws RewardDataException {
                decodes.incrementAndGet(); return delegate.decode(value);
            }
            @Override public JsonObject encode(net.minecraft.world.item.ItemStack value) throws RewardDataException { return delegate.encode(value); }
        };
        @SuppressWarnings("unchecked")
        RewardMerger<net.minecraft.world.item.ItemStack> merger = (RewardMerger<net.minecraft.world.item.ItemStack>) original.getMerger();
        SakuraRewards.register(RewardTypeDefinition.builder(SakuraRewardTypes.ITEM, codec, (context, value) -> RewardGrantResult.success())
                .validator(RewardValidator.acceptAll()).describer((language, value, amount) -> xin.vanilla.sakura.SakuraComponent.get().literal("item"))
                .merger(merger).addPermission(original.getAddPermission()).build());
        for (RewardTypeDefinition<?> definition : definitions) if (!definition.getId().equals(SakuraRewardTypes.ITEM)) SakuraRewards.register(definition);
        RewardList result = RewardManager.mergeRewards(new RewardList(Arrays.asList(item(1, 1), item(1, 2))));
        assertEquals(1, result.size()); assertEquals(4, decodes.get());
    }

    private static Reward item(int key, int count) {
        JsonObject value = new JsonObject(); value.addProperty("item", "minecraft:apple"); value.addProperty("count", count);
        value.addProperty("nbt", "{probe:{key:" + key + ",label:\"point-\u5bb6\",nested:[{x:1},{x:2}]}}");
        return new Reward(value, SakuraRewardTypes.ITEM);
    }
    private static Reward number(int value) {
        JsonObject json = new JsonObject(); json.addProperty("expPoint", value); return new Reward(json, SakuraRewardTypes.EXPERIENCE_POINT);
    }
    private static RewardList legacy(RewardList input) {
        RewardList result = new RewardList();
        for (Reward candidate : input) {
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                Optional<Reward> value = RewardOperations.merge(result.get(i), candidate);
                if (value.isPresent()) { result.set(i, value.get()); merged = true; break; }
            }
            if (!merged) result.add(candidate);
        }
        return result;
    }
    private static RewardTypeId extension(String name, boolean changes, Runnable onDecode) {
        RewardTypeId id = RewardTypeId.parse("test:" + name);
        RewardCodec<Integer> codec = new RewardCodec<Integer>() {
            public Integer decode(JsonObject value) { if (onDecode != null) onDecode.run(); return value.get("value").getAsInt(); }
            public JsonObject encode(Integer value) { JsonObject result = new JsonObject(); result.addProperty("value", value); return result; }
        };
        SakuraRewards.register(RewardTypeDefinition.builder(id, codec, (context, value) -> RewardGrantResult.success())
                .validator(RewardValidator.acceptAll()).describer((lang, value, amount) -> xin.vanilla.sakura.SakuraComponent.get().literal(String.valueOf(value)))
                .merger(new RewardMerger<Integer>() {
                    public RewardMergeKey key(Integer value) { return RewardMergeKey.of(changes ? String.valueOf(value % 2) : "all"); }
                    public Optional<Integer> merge(Integer first, Integer second) { return changes || first < 4 ? Optional.of(first + second) : Optional.empty(); }
                }).addPermission(RewardAddPermission.of(0, "test:reward.add." + name)).build());
        return id;
    }
}
