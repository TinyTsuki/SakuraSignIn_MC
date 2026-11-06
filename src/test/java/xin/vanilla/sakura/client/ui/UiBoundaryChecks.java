package xin.vanilla.sakura.client.ui;

import org.objectweb.asm.ClassReader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class UiBoundaryChecks {
    private UiBoundaryChecks() {
    }

    static Map<String, byte[]> readZip(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return readZip(input);
        }
    }

    private static Map<String, byte[]> readZip(InputStream input) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                byte[] bytes = zip.readNBytes(64 * 1024 * 1024 + 1);
                total = Math.addExact(total, bytes.length);
                if (bytes.length > 64 * 1024 * 1024 || total > 128 * 1024 * 1024 || files.size() >= 20000)
                    throw new IOException("Archive budget exceeded");
                if (files.putIfAbsent(entry.getName(), bytes) != null) throw new IOException("Duplicate archive entry");
            }
        }
        return files;
    }

    static List<String> artifact(Map<String, byte[]> files, String owner, boolean merged) {
        return artifact(files, owner, merged, 0);
    }

    private static List<String> artifact(Map<String, byte[]> files, String owner, boolean merged, int depth) {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = entry.getKey(), lower = name.toLowerCase(Locale.ROOT);
            byte[] bytes = entry.getValue();
            if (!names.add(lower) || name.startsWith("/") || name.contains("\\") || name.contains(":")
                    || Arrays.asList(name.split("/")).contains("..")) errors.add("Unsafe path " + name);
            if (lower.startsWith("com/sighs/") || lower.startsWith("docs/") || lower.equals("agents.md") || lower.endsWith("/agents.md")
                    || lower.contains("banira-aui-compile-api") || lower.matches(".*(?:test|probe)\\.(class|java)$")
                    || lower.contains("/src/test/") || lower.contains("/test/"))
                errors.add("Non-product entry " + name);
            if (!owner.equals("xin/vanilla/banira/") && !merged && name.startsWith("xin/vanilla/banira/"))
                errors.add("Embedded SDK " + name);
            if (name.endsWith(".class")) {
                try {
                    if (bytes.length < 8 || bytes[0] != (byte) 0xca || bytes[1] != (byte) 0xfe)
                        throw new IOException("Class header");
                    int major = (bytes[6] & 255) * 256 + (bytes[7] & 255);
                    if (major > 61) errors.add("Future class " + name);
                    else if (!new ClassReader(bytes).getClassName().concat(".class").equals(name))
                        errors.add("Class identity " + name);
                } catch (IOException | RuntimeException failure) {
                    errors.add("Malformed class " + name);
                }
            }
            if (name.endsWith(".jar")) {
                if (!merged || depth >= 3) errors.add("Unexpected embedded archive " + name);
                else try {
                    errors.addAll(artifact(readZip(new ByteArrayInputStream(bytes)), owner, true, depth + 1));
                } catch (IOException failure) {
                    errors.add("Malformed embedded archive " + name);
                }
            }
            if (lower.equals("meta-inf/manifest.mf") && new String(bytes, StandardCharsets.UTF_8).contains("Banira-AUI-Compile-Only"))
                errors.add("Compile-only manifest");
        }
        return errors;
    }

    static List<String> pom(byte[] bytes) {
        List<String> errors = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            var dependencies = document.getElementsByTagNameNS("*", "dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                String value = dependencies.item(i).getTextContent().toLowerCase(Locale.ROOT);
                if (value.matches("(?s).*(apricity|ufpduegy|qimfjqz4|auicompileapi|stub|probe).*"))
                    errors.add("Non-product POM dependency");
            }
        } catch (Exception failure) {
            errors.add("Invalid POM");
        }
        return errors;
    }

    static List<String> resources(Map<String, byte[]> files, boolean shared) {
        List<String> errors = new ArrayList<>();
        int css = 0, markup = 0, bitmap = 0, js = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            byte[] bytes = entry.getValue();
            if (name.endsWith(".css")) {
                css += bytes.length;
                if (bytes.length > (shared ? 16384 : 4096)) errors.add("CSS budget");
                if (!shared && name.endsWith("shared.css")) errors.add("Copied shared CSS");
            } else if (name.endsWith(".html")) {
                markup += bytes.length;
                if (bytes.length > 12288) errors.add("HTML budget");
            } else if (name.endsWith(".js")) js += bytes.length;
            else if (name.matches(".*\\.(png|jpg|jpeg|webp|gif)$")) bitmap += bytes.length;
            else errors.add("Unexpected UI resource " + name);
            if (name.matches(".*\\.(html|css|js)$")) {
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.matches("(?is).*(<script\\b|\\bon[a-z]+\\s*=|@font-face|@import|https?:|data:|javascript:).*"))
                    errors.add("Active or remote UI resource");
                var links = java.util.regex.Pattern.compile("(?i)(?:href|src)\\s*=\\s*['\"]([^'\"]+)['\"]|url\\(\\s*['\"]?([^)'\"]+)").matcher(text);
                while (links.find()) {
                    String target = (links.group(1) == null ? links.group(2) : links.group(1)).trim();
                    try {
                        java.net.URI uri = new java.net.URI(target);
                        if (uri.isAbsolute() || target.startsWith("/") || target.contains("\\")
                                || target.contains("..") && !java.util.Set.of("../banira_codex/shared.css", "../banira_codex/ui/config.css", "../banira_codex/ui/player-config-editor.css").contains(target)
                                && !(shared && target.equals("../shared.css"))) errors.add("External UI reference");
                    } catch (java.net.URISyntaxException failure) {
                        errors.add("Invalid UI reference");
                    }
                }
            }
        }
        int lotteryAllowance = Math.min(512, Math.max(0, files.getOrDefault("lottery.css", new byte[0]).length - 1320));
        if (css > 16384 + lotteryAllowance) errors.add("CSS budget");
        if (!shared) for (var page : files.entrySet())
            if (page.getKey().endsWith(".html")) {
                var links = java.util.regex.Pattern.compile("href=\"([^\"]+\\.css)\"").matcher(new String(page.getValue(), StandardCharsets.UTF_8));
                java.util.Set<String> loaded = new java.util.HashSet<>();
                int pageCss = 0;
                while (links.find()) if (loaded.add(links.group(1)) && files.containsKey(links.group(1)))
                    pageCss += files.get(links.group(1)).length;
                if (pageCss > 4096) errors.add("Page CSS budget");
            }
        if (css + markup > 262144) errors.add("HTML/CSS budget");
        if (js > 0) errors.add("Unexpected page script");
        if (bitmap > 65536) errors.add("Bitmap budget");
        return errors;
    }

    static Map<String, byte[]> ownResources(String modId) throws IOException {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity", modId);
        Map<String, byte[]> files = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return files;
    }
}
