import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standalone regression tests: compile with the inspector, then pass one or more Spark runtime jars. */
public final class SparkProfileInspectorTest {
    private static int failures;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Expected one or more Spark runtime jars");
        failures = 0;
        for (String arg : args) {
            final Path jar = Paths.get(arg);
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
                final boolean legacy = Arrays.stream(loader.loadClass(proto(loader) + "ThreadNode").getMethods())
                        .anyMatch(method -> method.getName().equals("getTime") && method.getParameterCount() == 0);
                String schema = legacy ? "legacy" : "modern";
                System.out.println("Spark runtime=" + jar + " schema=" + schema);
                check(schema + " units", () -> {
                    String output = inspect(jar, report(loader, 10000, thread(loader, legacy, "test", 100,
                            node("Work", "()V", 10, 100))));
                    contains(output, "intervalMs=10.0");
                    contains(output, "intervalMicros=10000");
                    contains(output, "sampledMs=100.0");
                    contains(output, "inclusiveMs=100.0 inclusivePct=100.00 selfMs=100.0 selfPct=100.00 app.Work#run()V");
                    contains(inspect(jar, report(loader, 250, thread(loader, legacy, "test", 1,
                            node("Work", "()V", 10, 1)))), "intervalMs=0.25");
                });
                check(schema + " recursion, overloads and threads", () -> {
                    Spec recursive = node("A", "()V", 22, 50, node("Leaf", "()V", 1, 40));
                    Spec outer = node("A", "()V", 11, 80, node("B", "()V", 1, 60, recursive));
                    Spec root = node("Root", "()V", 1, 120, outer,
                            node("A", "()V", 33, 20), node("A", "(I)V", 44, 20));
                    String output = inspect(jar, report(loader, 10000,
                            thread(loader, legacy, "first", 120, root),
                            thread(loader, legacy, "second", 3, node("A", "()V", 1, 3))));
                    String first = output.substring(output.indexOf("thread=first"), output.indexOf("thread=second"));
                    contains(first, "inclusiveMs=100.0 inclusivePct=83.33 selfMs=50.0 selfPct=41.67 app.A#run()V");
                    contains(first, "inclusiveMs=20.0 inclusivePct=16.67 selfMs=20.0 selfPct=16.67 app.A#run(I)V");
                    contains(first, "selfTotalMs=120.0");
                    contains(output.substring(output.indexOf("thread=second")),
                            "inclusiveMs=3.0 inclusivePct=100.00 selfMs=3.0 selfPct=100.00 app.A#run()V");
                });
                check(schema + " depth and hidden siblings", () -> {
                    Spec[] siblings = new Spec[7];
                    for (int n = 0; n < siblings.length; n++) siblings[n] = node("Sibling" + n, "()V", 1, 10);
                    Spec deep = node("Deep", "()V", 1, 100, siblings);
                    for (int n = 0; n < 24; n++) deep = node("Wrapper" + n, "()V", 1, 100, deep);
                    String output = inspect(jar, report(loader, 10000, thread(loader, legacy, "test", 100, deep)));
                    contains(output, "inclusiveMs=100.0 inclusivePct=100.00 selfMs=30.0 selfPct=30.00 app.Deep#run()V");
                    contains(output, "app.Sibling6#run()V");
                    contains(output, "maxDepth=25");
                    contains(output, "omitted");
                    contains(output, "selfTotalMs=100.0");
                });
                check(schema + " empty and unframed weight", () -> {
                    String output = inspect(jar, report(loader, 10000,
                            thread(loader, legacy, "empty", 0),
                            thread(loader, legacy, "test", 5, node("Work", "()V", 1, 3))));
                    contains(output, "unframedMs=2.0");
                    if (output.contains("NaN") || output.contains("Infinity")) throw new AssertionError(output);
                });
                check(schema + " empty and all-zero reports fail", () -> {
                    rejects(jar, report(loader, 10000), "no readable sample time");
                    rejects(jar, report(loader, 10000, thread(loader, legacy, "empty", 0)), "no readable sample time");
                });
                check(schema + " inconsistent weights", () -> {
                    String output = inspect(jar, report(loader, 10000, thread(loader, legacy, "snapshot", 1,
                            node("Parent", "()V", 1, 1, node("Child", "()V", 1, 2)))));
                    contains(output, "WARNING: inconsistentNodes=1 childExcessMs=1.0");
                    contains(output, "selfTotalMs=2.0");
                    contains(output, "negative self residuals clamped to zero");
                });
                if (!legacy) {
                    check("modern deep traversal", () -> {
                        Object builder = builder(loader, "ThreadNode");
                        call(builder, "setName", String.class, "deep");
                        call(builder, "addTimes", double.class, 1.0);
                        Class<?> nodeType = loader.loadClass(proto(loader) + "StackTraceNode");
                        for (int n = 0; n < 5000; n++) {
                            Object nb = builder(loader, "StackTraceNode");
                            call(nb, "setClassName", String.class, "app.Recursive");
                            call(nb, "setMethodName", String.class, "run");
                            call(nb, "setMethodDesc", String.class, "()V");
                            call(nb, "addTimes", double.class, 1.0);
                            if (n > 0) call(nb, "addChildrenRefs", int.class, n - 1);
                            call(builder, "addChildren", nodeType, build(nb));
                        }
                        call(builder, "addChildrenRefs", int.class, 4999);
                        String output = inspect(jar, report(loader, 10000, build(builder)));
                        contains(output, "maxDepth=4999");
                        contains(output, "inclusiveMs=1.0 inclusivePct=100.00 selfMs=1.0 selfPct=100.00 app.Recursive#run()V");
                    });
                    check("modern invalid reference", () -> rejects(jar,
                            malformed(loader, 3), "reference"));
                    check("modern cyclic reference", () -> rejects(jar,
                            malformed(loader, 0), "cycle"));
                    check("modern allocation mode", () -> {
                        Object metadata = builder(loader, "SamplerMetadata");
                        Class<?> mode = loader.loadClass(proto(loader) + "SamplerMetadata$SamplerMode");
                        call(metadata, "setSamplerMode", mode, mode.getField("ALLOCATION").get(null));
                        Object report = builder(loader, "SamplerData");
                        call(report, "setMetadata", loader.loadClass(proto(loader) + "SamplerMetadata"), build(metadata));
                        rejects(jar, build(report), "Unsupported sampler mode");
                    });
                }
            }
        }
        if (failures != 0) throw new AssertionError(failures + " test(s) failed");
        System.out.println("All SparkProfileInspector tests passed");
    }

    private static Object malformed(ClassLoader loader, int childRef) throws Exception {
        Object node = builder(loader, "StackTraceNode");
        call(node, "setClassName", String.class, "app.Bad");
        call(node, "addTimes", double.class, 1.0);
        call(node, "addChildrenRefs", int.class, childRef);
        Object thread = builder(loader, "ThreadNode");
        call(thread, "addTimes", double.class, 1.0);
        call(thread, "addChildren", loader.loadClass(proto(loader) + "StackTraceNode"), build(node));
        call(thread, "addChildrenRefs", int.class, 0);
        return report(loader, 10000, build(thread));
    }

    private static Object thread(ClassLoader loader, boolean legacy, String name, double time, Spec... roots) throws Exception {
        Object thread = builder(loader, "ThreadNode");
        call(thread, "setName", String.class, name);
        times(thread, legacy, time);
        List<Object> nodes = new ArrayList<Object>();
        Class<?> nodeType = loader.loadClass(proto(loader) + "StackTraceNode");
        for (Spec root : roots) {
            Object value = buildNode(loader, legacy, root, nodes);
            if (legacy) call(thread, "addChildren", nodeType, value);
            else call(thread, "addChildrenRefs", int.class, nodes.size() - 1);
        }
        if (!legacy) for (Object node : nodes) call(thread, "addChildren", nodeType, node);
        return build(thread);
    }

    private static Object buildNode(ClassLoader loader, boolean legacy, Spec spec, List<Object> nodes) throws Exception {
        Object node = builder(loader, "StackTraceNode");
        call(node, "setClassName", String.class, "app." + spec.name);
        call(node, "setMethodName", String.class, "run");
        call(node, "setMethodDesc", String.class, spec.desc);
        call(node, "setLineNumber", int.class, spec.line);
        times(node, legacy, spec.time);
        for (Spec child : spec.children) {
            Object value = buildNode(loader, legacy, child, nodes);
            if (legacy) call(node, "addChildren", loader.loadClass(proto(loader) + "StackTraceNode"), value);
            else call(node, "addChildrenRefs", int.class, nodes.size() - 1);
        }
        Object value = build(node);
        nodes.add(value);
        return value;
    }

    private static void times(Object builder, boolean legacy, double time) throws Exception {
        if (legacy) call(builder, "setTime", double.class, time);
        else {
            call(builder, "addTimes", double.class, time * 0.25);
            call(builder, "addTimes", double.class, time * 0.75);
        }
    }

    private static Object report(ClassLoader loader, int interval, Object... threads) throws Exception {
        Object metadata = builder(loader, "SamplerMetadata");
        call(metadata, "setInterval", int.class, interval);
        Object report = builder(loader, "SamplerData");
        call(report, "setMetadata", loader.loadClass(proto(loader) + "SamplerMetadata"), build(metadata));
        for (Object thread : threads) call(report, "addThreads", loader.loadClass(proto(loader) + "ThreadNode"), thread);
        return build(report);
    }

    private static String inspect(Path jar, Object report) throws Exception {
        Path input = Files.createTempFile("spark-inspector-test-", ".sparkprofile");
        Path summary = Files.createTempFile("spark-inspector-test-", ".txt");
        PrintStream original = System.out;
        try (PrintStream quiet = new PrintStream(new ByteArrayOutputStream())) {
            Files.write(input, (byte[]) report.getClass().getMethod("toByteArray").invoke(report));
            System.setOut(quiet);
            SparkProfileInspector.main(new String[]{jar.toString(), input.toString(), summary.toString()});
            return new String(Files.readAllBytes(summary), StandardCharsets.UTF_8);
        } finally {
            System.setOut(original);
            Files.deleteIfExists(input);
            Files.deleteIfExists(summary);
        }
    }

    private static void rejects(Path jar, Object report, String expected) throws Exception {
        try {
            inspect(jar, report);
        } catch (IllegalArgumentException e) {
            contains(e.getMessage(), expected);
            return;
        }
        throw new AssertionError("Expected rejection containing: " + expected);
    }

    private static String proto(ClassLoader loader) throws ClassNotFoundException {
        try {
            loader.loadClass("me.lucko.spark.proto.SparkSamplerProtos$SamplerData");
            return "me.lucko.spark.proto.SparkSamplerProtos$";
        } catch (ClassNotFoundException ignored) {
            loader.loadClass("me.lucko.spark.proto.SparkProtos$SamplerData");
            return "me.lucko.spark.proto.SparkProtos$";
        }
    }

    private static Object builder(ClassLoader loader, String type) throws Exception {
        return loader.loadClass(proto(loader) + type).getMethod("newBuilder").invoke(null);
    }

    private static Object build(Object builder) throws Exception {
        return builder.getClass().getMethod("build").invoke(builder);
    }

    private static void call(Object target, String method, Class<?> type, Object arg) throws Exception {
        target.getClass().getMethod(method, type).invoke(target, arg);
    }

    private static void contains(String actual, String expected) {
        if (!actual.contains(expected)) throw new AssertionError("Missing: " + expected);
    }

    private static void check(String name, Test test) throws Exception {
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (AssertionError | Exception e) {
            failures++;
            System.out.println("FAIL " + name + ": " + e);
        }
    }

    private interface Test {
        void run() throws Exception;
    }

    private static Spec node(String name, String desc, int line, double time, Spec... children) {
        return new Spec(name, desc, line, time, Arrays.asList(children));
    }

    private static final class Spec {
        final String name;
        final String desc;
        final int line;
        final double time;
        final List<Spec> children;

        Spec(String name, String desc, int line, double time, List<Spec> children) {
            this.name = name;
            this.desc = desc;
            this.line = line;
            this.time = time;
            this.children = children;
        }
    }
}
