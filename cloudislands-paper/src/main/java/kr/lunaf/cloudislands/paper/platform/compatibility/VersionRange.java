package kr.lunaf.cloudislands.paper.platform.compatibility;

public record VersionRange(String id, int major, int minor) {
    public VersionRange {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("range id is required");
        }
    }

    public static VersionRange majorMinor(String id, int major, int minor) {
        return new VersionRange(id, major, minor);
    }

    public boolean includes(ServerVersion version) {
        return version != null && version.major() == major && version.minor() == minor;
    }

    public String summary() {
        return id + "=" + major + "." + minor + ".x";
    }
}
