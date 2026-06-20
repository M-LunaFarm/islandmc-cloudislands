package kr.lunaf.cloudislands.api.compat;

public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {
    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version parts must be non-negative");
        }
    }

    public static ApiVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("version is blank");
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("version must use major.minor.patch");
        }
        return new ApiVersion(number(parts[0]), number(parts[1]), number(parts[2]));
    }

    public boolean sameMajor(ApiVersion other) {
        return other != null && major == other.major;
    }

    public boolean atLeast(ApiVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        if (other == null) {
            return 1;
        }
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("version part is not numeric", ex);
        }
    }
}
