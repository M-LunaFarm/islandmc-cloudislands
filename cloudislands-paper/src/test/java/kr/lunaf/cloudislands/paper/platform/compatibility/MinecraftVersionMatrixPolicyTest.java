package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MinecraftVersionMatrixPolicyTest {
    @Test
    void currentMatrixIsTheSourceOfTruthForTasksPackagingAndReadme() throws Exception {
        Path root = repositoryRoot();
        Matrix matrix = Matrix.parse(Files.readString(root.resolve("gradle/minecraft-versions.toml")));
        String build = Files.readString(root.resolve("build.gradle.kts"));
        String settings = Files.readString(root.resolve("settings.gradle.kts"));
        String readme = Files.readString(root.resolve("README.md"));

        matrix.validate(root);

        assertEquals(List.of("paper-1.21", "paper-26.1", "paper-26.2"), matrix.ids());
        assertEquals("paper-1.21", matrix.latestStable().id());
        assertFalse(matrix.entries().stream().anyMatch(entry -> entry.experimental() && entry.releaseSupported()));
        assertTrue(build.contains("verifyMinecraftVersionMatrix"));
        assertTrue(build.contains("compileAllMinecraftVersions"));
        assertTrue(build.contains("bootSmokeAllStableMinecraftVersions"));
        assertTrue(build.contains("verifyAdapterPackaging"));
        assertTrue(build.contains("verifyReadmeVersionTable"));
        assertTrue(settings.contains("cloudislandsVersionMatrixFile"));
        assertTrue(readme.contains(matrix.readmeBlock()));
    }

    @Test
    void matrixValidationRejectsDuplicateIdsAndOverlappingRanges() {
        String source = """
            [[versions]]
            id = "paper-26.1"
            normalizedRange = "26.1.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter"
            releaseSupported = false
            experimental = true

            [[versions]]
            id = "paper-26.1"
            normalizedRange = "26.2.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter"
            releaseSupported = false
            experimental = true
            """;

        IllegalStateException duplicate = assertThrows(IllegalStateException.class, () -> Matrix.parse(source).validate(repositoryRoot()));
        assertTrue(duplicate.getMessage().contains("Duplicate id"));

        String overlap = """
            [[versions]]
            id = "paper-26.1"
            normalizedRange = "26.1.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter"
            releaseSupported = false
            experimental = true

            [[versions]]
            id = "paper-26.2"
            normalizedRange = "26.1.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter"
            releaseSupported = false
            experimental = true
            """;
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> Matrix.parse(overlap).validate(repositoryRoot()));
        assertTrue(failure.getMessage().contains("Duplicate normalizedRange"));
    }

    @Test
    void matrixValidationRejectsGapsMissingRequiredReleaseExperimentalAndMissingAdapters() throws Exception {
        Path root = repositoryRoot();
        Matrix gap = Matrix.parse("""
            [[versions]]
            id = "paper-1.21"
            normalizedRange = "1.21.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper121FamilyAdapter"
            releaseSupported = true
            experimental = false

            [[versions]]
            id = "paper-26.1"
            normalizedRange = "26.1.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter"
            releaseSupported = false
            experimental = true

            [[versions]]
            id = "paper-26.2"
            normalizedRange = "26.2.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter"
            releaseSupported = false
            experimental = true

            [[versions]]
            id = "paper-26.4"
            normalizedRange = "26.4.x"
            adapterProject = "cloudislands-paper"
            adapterClass = "kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter"
            releaseSupported = false
            experimental = true
            """);
        assertTrue(assertThrows(IllegalStateException.class, () -> gap.validate(root)).getMessage().contains("gap"));

        Matrix missingRequired = Matrix.parse(Files.readString(root.resolve("gradle/minecraft-versions.toml"))
            .replace("id = \"paper-26.2\"", "id = \"paper-26.3\"")
            .replace("normalizedRange = \"26.2.x\"", "normalizedRange = \"26.3.x\""));
        assertTrue(assertThrows(IllegalStateException.class, () -> missingRequired.validate(root)).getMessage().contains("Required range"));

        Matrix experimentalRelease = Matrix.parse(Files.readString(root.resolve("gradle/minecraft-versions.toml")).replace("releaseSupported = false\nexperimental = true", "releaseSupported = true\nexperimental = true"));
        assertTrue(assertThrows(IllegalStateException.class, () -> experimentalRelease.validate(root)).getMessage().contains("Experimental"));

        Matrix missingAdapter = Matrix.parse(Files.readString(root.resolve("gradle/minecraft-versions.toml")).replace("Paper262Adapter", "Paper263Adapter"));
        assertTrue(assertThrows(IllegalStateException.class, () -> missingAdapter.validate(root)).getMessage().contains("adapterClass"));
    }

    @Test
    void unsupportedFutureVersionsDoNotFallBackToTheNewestAdapter() {
        UnsupportedPaperVersionException failure = assertThrows(
            UnsupportedPaperVersionException.class,
            () -> PaperVersionAdapterRegistry.defaults().select("26.3")
        );

        assertTrue(failure.getMessage().contains("normalized=26.3.0"));
        assertTrue(failure.getMessage().contains("paper-26.2"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private record Matrix(List<Entry> entries) {
        static Matrix parse(String source) {
            List<Entry> entries = new ArrayList<>();
            Map<String, String> current = new LinkedHashMap<>();
            for (String rawLine : source.split("\\R")) {
                String line = rawLine.split("#", 2)[0].trim();
                if (line.isBlank()) {
                    continue;
                }
                if (line.equals("[[versions]]")) {
                    if (!current.isEmpty()) {
                        entries.add(entry(current));
                        current = new LinkedHashMap<>();
                    }
                    continue;
                }
                String[] parts = line.split("=", 2);
                current.put(parts[0].trim(), scalar(parts[1].trim()));
            }
            if (!current.isEmpty()) {
                entries.add(entry(current));
            }
            return new Matrix(List.copyOf(entries));
        }

        void validate(Path root) {
            duplicates("id", entries.stream().map(Entry::id).toList());
            duplicates("normalizedRange", entries.stream().map(Entry::normalizedRange).toList());
            Set<String> ranges = new HashSet<>(entries.stream().map(Entry::normalizedRange).toList());
            for (String required : List.of("1.21.x", "26.1.x", "26.2.x")) {
                if (!ranges.contains(required)) {
                    throw new IllegalStateException("Required range missing: " + required);
                }
            }
            entries.stream()
                .map(Entry::range)
                .collect(java.util.stream.Collectors.groupingBy(Range::major))
                .forEach((major, values) -> {
                    List<Range> sorted = values.stream().sorted().toList();
                    for (int index = 1; index < sorted.size(); index++) {
                        if (sorted.get(index).minor() != sorted.get(index - 1).minor() + 1) {
                            throw new IllegalStateException("gap in " + major + ".x");
                        }
                    }
                });
            for (Entry entry : entries) {
                if (entry.experimental() && entry.releaseSupported()) {
                    throw new IllegalStateException("Experimental entry cannot be releaseSupported");
                }
                Path module = root.resolve(entry.adapterProject());
                if (!Files.exists(module.resolve("build.gradle.kts"))) {
                    throw new IllegalStateException("missing adapterProject " + entry.adapterProject());
                }
                Path adapter = module.resolve("src/main/java/" + entry.adapterClass().replace('.', '/') + ".java");
                if (!Files.exists(adapter)) {
                    throw new IllegalStateException("missing adapterClass " + entry.adapterClass());
                }
            }
        }

        List<String> ids() {
            return entries.stream().map(Entry::id).toList();
        }

        Entry latestStable() {
            return entries.stream()
                .filter(entry -> entry.releaseSupported() && !entry.experimental())
                .max(Comparator.comparing(Entry::range))
                .orElseThrow();
        }

        String readmeBlock() {
            List<String> lines = new ArrayList<>();
            lines.add("<!-- minecraft-version-matrix:start -->");
            lines.add("| Target | Compile | Boot smoke | Release | Notes |");
            lines.add("|---|---|---|---|---|");
            entries.stream().sorted(Comparator.comparing(Entry::range)).forEach(entry -> lines.add(entry.readmeRow()));
            lines.add("<!-- minecraft-version-matrix:end -->");
            return String.join("\n", lines);
        }

        private static void duplicates(String label, List<String> values) {
            Set<String> seen = new HashSet<>();
            for (String value : values) {
                if (!seen.add(value)) {
                    throw new IllegalStateException("Duplicate " + label + ": " + value);
                }
            }
        }

        private static Entry entry(Map<String, String> values) {
            return new Entry(
                values.get("id"),
                values.get("normalizedRange"),
                values.get("adapterProject"),
                values.get("adapterClass"),
                Boolean.parseBoolean(values.get("bootSmokeEnabled")),
                Boolean.parseBoolean(values.get("releaseSupported")),
                Boolean.parseBoolean(values.get("experimental")),
                values.getOrDefault("notes", "")
            );
        }

        private static String scalar(String value) {
            return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
        }
    }

    private record Entry(
        String id,
        String normalizedRange,
        String adapterProject,
        String adapterClass,
        boolean bootSmokeEnabled,
        boolean releaseSupported,
        boolean experimental,
        String notes
    ) {
        Range range() {
            String[] parts = normalizedRange.replace(".x", "").split("\\.");
            return new Range(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        String readmeRow() {
            String suffix = id.chars().filter(Character::isDigit).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
            String boot = bootSmokeEnabled ? "`paper" + suffix + "BootSmoke`" : "pending official Paper build";
            String release = releaseSupported ? "release-supported" : experimental ? "experimental compile-only" : "not release-supported";
            return "| Paper `" + normalizedRange + "` | `paper" + suffix + "Compile` | " + boot + " | " + release + " | " + notes + " |";
        }
    }

    private record Range(int major, int minor) implements Comparable<Range> {
        @Override
        public int compareTo(Range other) {
            int majorCompare = Integer.compare(major, other.major);
            return majorCompare != 0 ? majorCompare : Integer.compare(minor, other.minor);
        }
    }
}
