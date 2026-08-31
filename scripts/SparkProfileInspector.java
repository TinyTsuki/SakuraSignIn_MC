import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a native Spark sampler protobuf without linking against a specific Spark version.
 * The development Gradle task passes the exact runtime jar used to write the report.
 */
public final class SparkProfileInspector {
    private static final int TOP_NODES = 6;
    private static final int MAX_DEPTH = 20;

    private final StringBuilder output = new StringBuilder();

    private SparkProfileInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: SparkProfileInspector <spark.jar> <report.sparkprofile> <summary.txt>");
        }
        SparkProfileInspector inspector = new SparkProfileInspector();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, null)) {
            inspector.inspect(loader, Paths.get(args[1]));
        }
        Path summary = Paths.get(args[2]);
        Files.createDirectories(summary.getParent());
        Files.write(summary, inspector.output.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(inspector.output);
    }

    private void inspect(ClassLoader loader, Path reportPath) throws Exception {
        Class<?> samplerDataType = samplerDataType(loader);
        Object report = samplerDataType.getMethod("parseFrom", byte[].class).invoke(null, Files.readAllBytes(reportPath));
        Object metadata = invoke(report, "getMetadata");
        line("report=" + reportPath.toAbsolutePath());
        line("comment=" + invoke(metadata, "getComment"));
        line("intervalMs=" + invoke(metadata, "getInterval") + optionalMetadata(metadata));

        List<?> threads = list(report, "getThreadsList");
        line("threads=" + threads.size());
        boolean legacy = hasMethod(threads.isEmpty() ? null : threads.get(0), "getTime");
        double capturedSamples = 0.0D;
        for (Object thread : threads) {
            if (legacy) capturedSamples += inspectLegacyThread(thread);
            else capturedSamples += inspectModernThread(thread);
        }
        if (!threads.isEmpty() && capturedSamples <= 0.0D) {
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

    private double inspectLegacyThread(Object thread) throws Exception {
        double samples = number(invoke(thread, "getTime"));
        line("thread=" + invoke(thread, "getName") + " samples=" + samples
                + " roots=" + list(thread, "getChildrenList").size());
        List<Object> roots = new ArrayList<Object>(list(thread, "getChildrenList"));
        sortLegacy(roots);
        for (Object root : limit(roots)) printLegacy(root, "  ", 0);
        return samples;
    }

    private void printLegacy(Object node, String indent, int depth) throws Exception {
        line(indent + number(invoke(node, "getTime")) + " " + frame(node));
        if (depth >= MAX_DEPTH) return;
        List<Object> children = new ArrayList<Object>(list(node, "getChildrenList"));
        sortLegacy(children);
        for (Object child : limit(children)) printLegacy(child, indent + "  ", depth + 1);
    }

    private double inspectModernThread(Object thread) throws Exception {
        List<Object> nodes = new ArrayList<Object>(list(thread, "getChildrenList"));
        List<Integer> roots = integers(thread, "getChildrenRefsList");
        double samples = sum(list(thread, "getTimesList"));
        line("thread=" + invoke(thread, "getName") + " samples=" + number(samples)
                + " nodes=" + nodes.size() + " roots=" + roots.size());
        sortModern(roots, nodes);
        for (Integer root : limit(roots)) printModern(nodes, root.intValue(), "  ", 0, new HashSet<Integer>());
        return samples;
    }

    private void printModern(List<Object> nodes, int index, String indent, int depth, Set<Integer> lineage) throws Exception {
        if (index < 0 || index >= nodes.size() || !lineage.add(Integer.valueOf(index))) return;
        Object node = nodes.get(index);
        line(indent + number(sum(list(node, "getTimesList"))) + " " + frame(node));
        if (depth < MAX_DEPTH) {
            List<Integer> children = integers(node, "getChildrenRefsList");
            sortModern(children, nodes);
            for (Integer child : limit(children)) printModern(nodes, child.intValue(), indent + "  ", depth + 1, lineage);
        }
        lineage.remove(Integer.valueOf(index));
    }

    private static void sortLegacy(List<Object> nodes) {
        nodes.sort(Comparator.comparingDouble(SparkProfileInspector::legacyTime).reversed());
    }

    private static double legacyTime(Object node) {
        try {
            return number(invoke(node, "getTime"));
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private static void sortModern(List<Integer> indexes, final List<Object> nodes) {
        indexes.sort(Comparator.<Integer>comparingDouble(index -> modernTime(nodes, index.intValue())).reversed());
    }

    private static double modernTime(List<Object> nodes, int index) {
        try {
            return index >= 0 && index < nodes.size() ? sum(list(nodes.get(index), "getTimesList")) : 0.0D;
        } catch (Exception ignored) {
            return 0.0D;
        }
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

    private static <T> List<T> limit(List<T> values) {
        return values.subList(0, Math.min(TOP_NODES, values.size()));
    }
}
