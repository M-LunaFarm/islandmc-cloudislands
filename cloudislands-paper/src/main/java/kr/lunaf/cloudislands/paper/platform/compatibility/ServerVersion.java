package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ServerVersion(
    int major,
    int minor,
    int patch,
    String preRelease,
    String buildMetadata,
    String original
) implements Comparable<ServerVersion> {
    private static final Pattern MC_VERSION = Pattern.compile("MC:\\s*([0-9A-Za-z.+-]+)");
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?.*$");

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
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported Paper version format: " + original);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return new ServerVersion(
            major,
            minor,
            patch,
            Objects.toString(matcher.group(4), ""),
            Objects.toString(matcher.group(5), ""),
            original
        );
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
        Matcher mcVersion = MC_VERSION.matcher(rawVersion);
        if (mcVersion.find()) {
            return mcVersion.group(1);
        }
        return rawVersion;
    }
}
