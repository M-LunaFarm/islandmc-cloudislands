package kr.lunaf.cloudislands.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SuperiorSkyblock2MigrationScanner {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public ScanResult scan(Path superiorSkyblockDataPath) {
        List<MigrationManifest> manifests = new ArrayList<>();
        List<MigrationIssue> issues = new ArrayList<>();
        Path islandsDir = findIslandsDirectory(superiorSkyblockDataPath);
        if (islandsDir == null || !Files.isDirectory(islandsDir)) {
            issues.add(new MigrationIssue("ISLAND_DIRECTORY_NOT_FOUND", "could not find SuperiorSkyblock2 island data directory under " + superiorSkyblockDataPath, true));
            return new ScanResult(List.of(), issues);
        }
        try (Stream<Path> files = Files.walk(islandsDir, 3)) {
            files.filter(Files::isRegularFile)
                .filter(this::looksLikeIslandFile)
                .forEach(file -> scanFile(file, manifests, issues));
        } catch (IOException exception) {
            issues.add(new MigrationIssue("SCAN_FAILED", exception.getMessage(), true));
        }
        return new ScanResult(List.copyOf(manifests), List.copyOf(issues));
    }

    private Path findIslandsDirectory(Path root) {
        List<Path> candidates = List.of(
            root.resolve("islands"),
            root.resolve("database").resolve("islands"),
            root.resolve("data").resolve("islands"),
            root.resolve("SuperiorSkyblock2").resolve("islands")
        );
        return candidates.stream().filter(Files::isDirectory).findFirst().orElse(root);
    }

    private boolean looksLikeIslandFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private void scanFile(Path file, List<MigrationManifest> manifests, List<MigrationIssue> issues) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            UUID islandId = parseUuid(content, "islandId", parseUuid(content, "uuid", uuidFromFilename(file)));
            UUID ownerUuid = parseUuid(content, "owner", parseUuid(content, "ownerUuid", new UUID(0L, 0L)));
            if (ownerUuid.getMostSignificantBits() == 0L && ownerUuid.getLeastSignificantBits() == 0L) {
                issues.add(new MigrationIssue("OWNER_NOT_FOUND", "missing owner uuid in " + file, true));
                return;
            }
            int size = parseInt(content, "size", 300);
            long level = parseLong(content, "level", 0L);
            String worth = parseString(content, "worth", "0.00");
            List<UUID> members = parseUuidList(content, "members", "islandMembers", "coopMembers", "coops");
            List<UUID> bannedVisitors = parseUuidList(content, "bans", "bannedPlayers", "bannedVisitors", "visitorBans");
            LinkedHashSet<UUID> allMembers = new LinkedHashSet<>();
            allMembers.add(ownerUuid);
            allMembers.addAll(members);
            manifests.add(new MigrationManifest(islandId, ownerUuid, List.copyOf(allMembers), bannedVisitors, size, level, worth));
        } catch (RuntimeException | IOException exception) {
            issues.add(new MigrationIssue("ISLAND_FILE_PARSE_FAILED", file + ": " + exception.getMessage(), true));
        }
    }

    private UUID uuidFromFilename(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        String raw = dot < 0 ? name : name.substring(0, dot);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private UUID parseUuid(String content, String key, UUID fallback) {
        try {
            return UUID.fromString(parseString(content, key, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String parseString(String content, String key, String fallback) {
        String jsonNeedle = "\"" + key + "\"";
        int jsonStart = content.indexOf(jsonNeedle);
        if (jsonStart >= 0) {
            int colon = content.indexOf(':', jsonStart + jsonNeedle.length());
            int quote = colon < 0 ? -1 : content.indexOf('"', colon + 1);
            int end = quote < 0 ? -1 : content.indexOf('"', quote + 1);
            if (end > quote) {
                return content.substring(quote + 1, end);
            }
        }
        String yamlNeedle = key + ":";
        int yamlStart = content.indexOf(yamlNeedle);
        if (yamlStart >= 0) {
            int valueStart = yamlStart + yamlNeedle.length();
            int lineEnd = content.indexOf('\n', valueStart);
            String value = content.substring(valueStart, lineEnd < 0 ? content.length() : lineEnd).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private int parseInt(String content, String key, int fallback) {
        try {
            return Integer.parseInt(parseString(content, key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String content, String key, long fallback) {
        try {
            return Long.parseLong(parseString(content, key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<UUID> parseUuidList(String content, String... keys) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        for (String key : keys) {
            uuids.addAll(parseJsonUuidArray(content, key));
            uuids.addAll(parseYamlUuidBlock(content, key));
        }
        return List.copyOf(uuids);
    }

    private Set<UUID> parseJsonUuidArray(String content, String key) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        String needle = "\"" + key + "\"";
        int keyStart = content.indexOf(needle);
        if (keyStart < 0) {
            return uuids;
        }
        int arrayStart = content.indexOf('[', keyStart + needle.length());
        int arrayEnd = arrayStart < 0 ? -1 : content.indexOf(']', arrayStart + 1);
        if (arrayEnd <= arrayStart) {
            return uuids;
        }
        collectUuids(content.substring(arrayStart + 1, arrayEnd), uuids);
        return uuids;
    }

    private Set<UUID> parseYamlUuidBlock(String content, String key) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        String[] lines = content.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (!trimmed.startsWith(key + ":")) {
                continue;
            }
            String inline = trimmed.substring((key + ":").length()).trim();
            collectUuids(inline, uuids);
            int parentIndent = line.indexOf(trimmed);
            for (int child = index + 1; child < lines.length; child++) {
                String childLine = lines[child];
                String childTrimmed = childLine.trim();
                if (childTrimmed.isEmpty() || childTrimmed.startsWith("#")) {
                    continue;
                }
                int childIndent = childLine.indexOf(childTrimmed);
                if (childIndent <= parentIndent) {
                    break;
                }
                collectUuids(childTrimmed, uuids);
            }
            break;
        }
        return uuids;
    }

    private void collectUuids(String text, Set<UUID> uuids) {
        Matcher matcher = UUID_PATTERN.matcher(text);
        while (matcher.find()) {
            uuids.add(UUID.fromString(matcher.group()));
        }
    }

    public record ScanResult(List<MigrationManifest> manifests, List<MigrationIssue> issues) {}
}
