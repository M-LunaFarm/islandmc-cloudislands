package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.Objects;

public record ServerVersion(
    int major,
    int minor,
    int patch,
    String preRelease,
    String buildMetadata,
    String original
) implements Comparable<ServerVersion> {
    public ServerVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must be non-negative");
        }
        preRelease = preRelease == null ? "" : preRelease;
        buildMetadata = buildMetadata == null ? "" : buildMetadata;
        original = original == null ? "" : original;
    }

    public static ServerVersion parse(String rawVersion) {
        String original = rawVersion == null ? "" : rawVersion.trim();
        String value = extractMinecraftVersion(original);
        ParsedVersion parsed = parseValue(value, original);
        return new ServerVersion(
            parsed.major(),
            parsed.minor(),
            parsed.patch(),
            parsed.preRelease(),
            parsed.buildMetadata(),
            original
        );
    }

    private static ParsedVersion parseValue(String value, String original) {
        NumberPart major = readNumber(value, 0, original);
        int offset = major.nextOffset();
        if (offset >= value.length() || value.charAt(offset) != '.') {
            throw new IllegalArgumentException("Unsupported Paper version format: " + original);
        }
        NumberPart minor = readNumber(value, offset + 1, original);
        offset = minor.nextOffset();
        int patch = 0;
        if (offset < value.length() && value.charAt(offset) == '.') {
            NumberPart patchPart = readNumber(value, offset + 1, original);
            patch = patchPart.value();
            offset = patchPart.nextOffset();
        }

        String preRelease = "";
        String buildMetadata = "";
        if (offset < value.length() && value.charAt(offset) == '-') {
            int start = offset + 1;
            offset = readIdentifier(value, start);
            preRelease = value.substring(start, offset);
        }
        if (offset < value.length() && value.charAt(offset) == '+') {
            int start = offset + 1;
            offset = readIdentifier(value, start);
            buildMetadata = value.substring(start, offset);
        }
        return new ParsedVersion(major.value(), minor.value(), patch, preRelease, buildMetadata);
    }

    private static NumberPart readNumber(String value, int offset, String original) {
        int cursor = offset;
        while (cursor < value.length() && Character.isDigit(value.charAt(cursor))) {
            cursor++;
        }
        if (cursor == offset) {
            throw new IllegalArgumentException("Unsupported Paper version format: " + original);
        }
        return new NumberPart(Integer.parseInt(value.substring(offset, cursor)), cursor);
    }

    private static int readIdentifier(String value, int offset) {
        int cursor = offset;
        while (cursor < value.length() && versionIdentifierCharacter(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean versionIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '-';
    }

    public String normalized() {
        StringBuilder value = new StringBuilder()
            .append(major)
            .append('.')
            .append(minor)
            .append('.')
            .append(patch);
        if (!preRelease.isBlank()) {
            value.append('-').append(preRelease);
        }
        if (!buildMetadata.isBlank()) {
            value.append('+').append(buildMetadata);
        }
        return value.toString();
    }

    public boolean stable() {
        return preRelease.isBlank();
    }

    @Override
    public int compareTo(ServerVersion other) {
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        int patchCompare = Integer.compare(patch, other.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        if (preRelease.isBlank() && !other.preRelease.isBlank()) {
            return 1;
        }
        if (!preRelease.isBlank() && other.preRelease.isBlank()) {
            return -1;
        }
        return preRelease.compareTo(other.preRelease);
    }

    private static String extractMinecraftVersion(String rawVersion) {
        int marker = rawVersion.indexOf("MC:");
        if (marker >= 0) {
            int offset = marker + 3;
            while (offset < rawVersion.length() && Character.isWhitespace(rawVersion.charAt(offset))) {
                offset++;
            }
            int end = offset;
            while (end < rawVersion.length() && minecraftVersionCharacter(rawVersion.charAt(end))) {
                end++;
            }
            return rawVersion.substring(offset, end);
        }
        return rawVersion;
    }

    private static boolean minecraftVersionCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '+' || value == '-';
    }

    private record NumberPart(int value, int nextOffset) {
    }

    private record ParsedVersion(int major, int minor, int patch, String preRelease, String buildMetadata) {
        private ParsedVersion {
            preRelease = Objects.toString(preRelease, "");
            buildMetadata = Objects.toString(buildMetadata, "");
        }
    }
}
