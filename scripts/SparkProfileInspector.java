import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a native Spark sampler protobuf without linking against a specific Spark version.
 * The development Gradle task passes the exact runtime jar used to write the report.
 */
public final class SparkProfileInspector {
    private static final int TOP_NODES = 6;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_TREE_NODES = 120;
    private static final int DEFAULT_HOTSPOTS = 30;

    private final StringBuilder output = new StringBuilder();
    private final int hotspotLimit;

    private SparkProfileInspector(int hotspotLimit) {
        this.hotspotLimit = hotspotLimit;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Usage: SparkProfileInspector <spark.jar> <report.sparkprofile> <summary.txt> [hotspot-limit]");
        }
        int hotspotLimit = args.length == 4 ? Integer.parseInt(args[3]) : DEFAULT_HOTSPOTS;
        if (hotspotLimit <= 0) throw new IllegalArgumentException("hotspot-limit must be positive");
        SparkProfileInspector inspector = new SparkProfileInspector(hotspotLimit);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, null)) {
            inspector.inspect(loader, Paths.get(args[1]));
        }
        Path summary = Paths.get(args[2]).toAbsolutePath();
        Files.createDirectories(summary.getParent());
        Files.write(summary, inspector.output.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(inspector.output);
    }

    private void inspect(ClassLoader loader, Path reportPath) throws Exception {
        Class<?> samplerDataType = samplerDataType(loader);
        Object report = samplerDataType.getMethod("parseFrom", byte[].class).invoke(null, Files.readAllBytes(reportPath));
        Object metadata = invoke(report, "getMetadata");
        String mode = hasMethod(metadata, "getSamplerMode") ? invoke(metadata, "getSamplerMode").toString() : "EXECUTION";
        if (!"EXECUTION".equals(mode)) {
            throw new IllegalArgumentException("Unsupported sampler mode: " + mode + "; time rankings require EXECUTION.");
        }
        line("report=" + reportPath.toAbsolutePath());
        line("comment=" + invoke(metadata, "getComment"));
        // Both runtime jars schedule JavaSampler in MICROSECONDS and export interval unchanged.
        double intervalMicros = number(invoke(metadata, "getInterval"));
        line("intervalMs=" + intervalMicros / 1000.0D + " intervalMicros=" + invoke(metadata, "getInterval")
                + " samplerMode=" + mode + optionalMetadata(metadata));
        line("weights=sampled milliseconds, not sample counts or measured CPU time; waits may be included.");
        line("percentages=of each thread/group's sampledMs across all saved windows, not wall duration or all threads.");
        line("methods=class#method+descriptor (when available), merged across lines/callers; inclusive counts a method once per stack.");
        line("self=max(0, node weight minus ALL direct children); saved stack limits can leave unresolved callees in self time.");
        line("rankings traverse all saved depths; omitted tree/ranking entries do not establish absence of hotspots.");

        List<?> threads = list(report, "getThreadsList");
        line("threads=" + threads.size());
        double capturedSamples = 0.0D;
        for (Object thread : threads) {
            capturedSamples += inspectThread(thread);
        }
        if (capturedSamples <= 0.0D) {
            throw new IllegalArgumentException("Spark report contained no readable sample time; use the Spark jar that wrote it.");
        }
    }

    private String optionalMetadata(Object metadata) throws Exception {
        if (!hasMethod(metadata, "getEndTime") || !hasMethod(metadata, "getNumberOfTicks")) return "";
        long start = ((Number) invoke(metadata, "getStartTime")).longValue();
        long end = ((Number) invoke(metadata, "getEndTime")).longValue();
        return " durationMs=" + (end - start) + " ticks=" + invoke(metadata, "getNumberOfTicks");
    }

    private static Class<?> samplerDataType(ClassLoader loader) throws ClassNotFoundException {
        try {
            return loader.loadClass("me.lucko.spark.proto.SparkSamplerProtos$SamplerData");
        } catch (ClassNotFoundException ignored) {
            return loader.loadClass("me.lucko.spark.proto.SparkProtos$SamplerData");
        }
    }

    private double inspectThread(Object thread) throws Exception {
        boolean legacy = hasMethod(thread, "getTime");
        double sampledMs = time(thread, legacy);
        List<Object> raw = new ArrayList<Object>(list(thread, "getChildrenList"));
        List<Integer> roots = legacy ? new ArrayList<Integer>() : integers(thread, "getChildrenRefsList");
        if (legacy) for (int i = 0; i < raw.size(); i++) roots.add(i);
        List<Node> nodes = new ArrayList<Node>();
        // Flatten legacy messages iteratively; modern references already index a flat node table.
        for (int i = 0; i < raw.size(); i++) {
            Object value = raw.get(i);
            List<Integer> children = legacy ? new ArrayList<Integer>() : integers(value, "getChildrenRefsList");
            if (legacy) {
                for (Object child : list(value, "getChildrenList")) {
                    children.add(raw.size());
                    raw.add(child);
                }
            }
            String descriptor = hasMethod(value, "getMethodDesc") ? invoke(value, "getMethodDesc").toString() : "";
            String method = invoke(value, "getClassName") + "#" + invoke(value, "getMethodName")
                    + (descriptor.isEmpty() ? "[descriptor unavailable]" : descriptor);
            nodes.add(new Node(method, frame(value), time(value, legacy), children));
        }
        for (Node node : nodes) {
            double children = childTime(node.children, nodes);
            node.self = Math.max(0.0D, node.time - children);
            node.childExcess = Math.max(0.0D, children - node.time);
        }
        double rootTime = childTime(roots, nodes);
        double unframed = Math.max(0.0D, sampledMs - rootTime);
        double rootExcess = Math.max(0.0D, rootTime - sampledMs);
        Analysis analysis = analyze(nodes, roots);
        line("thread=" + invoke(thread, "getName") + " sampledMs=" + sampledMs
                + " nodes=" + nodes.size() + " roots=" + roots.size() + " schema=" + (legacy ? "legacy" : "modern"));
        line("  fullDepth nodesVisited=" + analysis.visits + " maxDepth=" + analysis.maxDepth
                + " methods=" + analysis.hotspots.size() + " selfTotalMs=" + analysis.selfTotal + " unframedMs=" + unframed);
        if (analysis.inconsistentNodes > 0 || rootExcess > 1.0E-6D) {
            line("  WARNING: inconsistentNodes=" + analysis.inconsistentNodes + " childExcessMs=" + analysis.childExcess
                    + " rootExcessMs=" + rootExcess + "; negative self residuals clamped to zero; recorded weights unchanged.");
            line("  Self totals/percentages may exceed sampledMs/100% for inconsistent saved weights.");
        }
        if (analysis.reached.size() != nodes.size()) {
            line("  WARNING: unreachableNodes=" + (nodes.size() - analysis.reached.size()) + "; not attributed to any saved root.");
        }
        printHotspots(analysis, sampledMs, false);
        printHotspots(analysis, sampledMs, true);
        line("  tree (inclusiveMs; maxDepth=" + MAX_DEPTH + ", childrenPerNode=" + TOP_NODES
                + ", nodeBudget=" + MAX_TREE_NODES + "; display limits only)");
        printTree(nodes, roots, "    ", 0, new int[]{MAX_TREE_NODES});
        return sampledMs;
    }

    private static Analysis analyze(List<Node> nodes, List<Integer> roots) {
        Analysis result = new Analysis();
        Set<Integer> lineage = new HashSet<Integer>();
        Set<String> methods = new HashSet<String>();
        Deque<Visit> stack = new ArrayDeque<Visit>();
        for (Integer root : roots) {
            stack.addLast(new Visit(root));
            while (!stack.isEmpty()) {
                Visit visit = stack.peekLast();
                Node node = nodes.get(visit.index);
                if (visit.nextChild == -1) {
                    if (!lineage.add(visit.index)) throw new IllegalArgumentException("Spark node reference cycle at " + node.frame);
                    Hotspot hotspot = result.hotspots.computeIfAbsent(node.method, Hotspot::new);
                    // Recursive occurrences have overlapping inclusive weight, but distinct self weight.
                    visit.ownsMethod = methods.add(node.method);
                    if (visit.ownsMethod) hotspot.inclusive += node.time;
                    hotspot.self += node.self;
                    if (node.self > hotspot.exampleSelf) {
                        hotspot.exampleSelf = node.self;
                        hotspot.example = exampleStack(stack, nodes);
                    }
                    result.selfTotal += node.self;
                    if (node.childExcess > 1.0E-6D) {
                        result.inconsistentNodes++;
                        result.childExcess += node.childExcess;
                    }
                    result.visits++;
                    result.reached.add(visit.index);
                    result.maxDepth = Math.max(result.maxDepth, stack.size() - 1);
                    visit.nextChild = 0;
                } else if (visit.nextChild < node.children.size()) {
                    stack.addLast(new Visit(node.children.get(visit.nextChild++)));
                } else {
                    if (visit.ownsMethod) methods.remove(node.method);
                    lineage.remove(visit.index);
                    stack.removeLast();
                }
            }
        }
        return result;
    }

    private void printHotspots(Analysis analysis, double total, boolean self) {
        List<Hotspot> ranked = new ArrayList<Hotspot>(analysis.hotspots.values());
        ranked.removeIf(hotspot -> (self ? hotspot.self : hotspot.inclusive) <= 0.0D);
        ranked.sort(Comparator.<Hotspot>comparingDouble(h -> self ? h.self : h.inclusive).reversed()
                .thenComparing(Comparator.<Hotspot>comparingDouble(h -> self ? h.inclusive : h.self).reversed())
                .thenComparing(h -> h.method));
        int shown = Math.min(hotspotLimit, ranked.size());
        line("  hotspots rank=" + (self ? "self" : "inclusive") + " shown=" + shown + " total=" + ranked.size()
                + " omitted=" + (ranked.size() - shown) + " (optional hotspot-limit controls rows, not traversal)");
        for (int i = 0; i < shown; i++) {
            Hotspot h = ranked.get(i);
            line("    " + (i + 1) + " inclusiveMs=" + h.inclusive + " inclusivePct=" + percent(h.inclusive, total)
                    + " selfMs=" + h.self + " selfPct=" + percent(h.self, total) + " " + h.method);
            if (self && i < 5) line("      exampleSelfMs=" + h.exampleSelf + " stack=" + h.example);
        }
    }

    private void printTree(List<Node> nodes, List<Integer> indexes, String indent, int depth, int[] budget) {
        List<Integer> sorted = new ArrayList<Integer>(indexes);
        sorted.sort(Comparator.<Integer>comparingDouble(index -> nodes.get(index).time).reversed()
                .thenComparing(index -> nodes.get(index).frame));
        int shown = 0;
        for (Integer index : sorted) {
            if (shown == TOP_NODES || budget[0] == 0) break;
            Node node = nodes.get(index);
            shown++;
            budget[0]--;
            line(indent + node.time + " " + node.frame);
            if (depth < MAX_DEPTH) printTree(nodes, node.children, indent + "  ", depth + 1, budget);
            else if (!node.children.isEmpty()) line(indent + "  ... depth limit: " + node.children.size() + " children omitted");
        }
        if (shown < sorted.size()) line(indent + "... " + (sorted.size() - shown) + " branches omitted (breadth/node budget)");
    }

    private static String exampleStack(Deque<Visit> stack, List<Node> nodes) {
        List<String> frames = new ArrayList<String>();
        Iterator<Visit> it = stack.descendingIterator();
        while (it.hasNext() && frames.size() < 6) frames.add(nodes.get(it.next().index).frame);
        Collections.reverse(frames);
        return (stack.size() > frames.size() ? "[... " + (stack.size() - frames.size()) + " callers] -> " : "")
                + String.join(" -> ", frames);
    }

    private static double time(Object value, boolean legacy) throws Exception {
        // Legacy AbstractNode.getTotalTime and modern EXECUTION's ProtoTimeEncoder divide microseconds by 1000.
        double time = legacy ? number(invoke(value, "getTime")) : sum(list(value, "getTimesList"));
        if (!Double.isFinite(time) || time < 0.0D) throw new IllegalArgumentException("Invalid Spark sample time: " + time);
        return time;
    }

    private static double childTime(List<Integer> indexes, List<Node> nodes) {
        double total = 0.0D;
        for (Integer index : indexes) {
            if (index < 0 || index >= nodes.size()) throw new IllegalArgumentException("Invalid Spark node reference: " + index);
            total += nodes.get(index).time;
        }
        return total;
    }

    private static String percent(double value, double total) {
        return String.format(Locale.ROOT, "%.2f", total > 0.0D ? 100.0D * value / total : 0.0D);
    }

    private static final class Node {
        final String method;
        final String frame;
        final double time;
        final List<Integer> children;
        double self;
        double childExcess;

        Node(String method, String frame, double time, List<Integer> children) {
            this.method = method;
            this.frame = frame;
            this.time = time;
            this.children = children;
        }
    }

    private static final class Visit {
        final int index;
        int nextChild = -1;
        boolean ownsMethod;

        Visit(int index) {
            this.index = index;
        }
    }

    private static final class Hotspot {
        final String method;
        double inclusive;
        double self;
        double exampleSelf;
        String example;

        Hotspot(String method) {
            this.method = method;
        }
    }

    private static final class Analysis {
        final Map<String, Hotspot> hotspots = new HashMap<String, Hotspot>();
        final Set<Integer> reached = new HashSet<Integer>();
        long visits;
        int maxDepth = -1;
        double selfTotal;
        int inconsistentNodes;
        double childExcess;
    }

    private static String frame(Object node) throws Exception {
        return invoke(node, "getClassName") + "#" + invoke(node, "getMethodName") + ':' + invoke(node, "getLineNumber");
    }

    private void line(String value) {
        output.append(value).append(System.lineSeparator());
    }

    private static List<?> list(Object target, String method) throws Exception {
        return (List<?>) invoke(target, method);
    }

    private static List<Integer> integers(Object target, String method) throws Exception {
        List<?> values = list(target, method);
        List<Integer> result = new ArrayList<Integer>(values.size());
        for (Object value : values) result.add(((Number) value).intValue());
        return result;
    }

    private static Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static boolean hasMethod(Object target, String method) {
        if (target == null) return false;
        for (java.lang.reflect.Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == 0) return true;
        }
        return false;
    }

    private static double sum(List<?> values) {
        double total = 0.0D;
        for (Object value : values) total += ((Number) value).doubleValue();
        return total;
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

}
