package kr.lunaf.cloudislands.migration;

public record MigrationLocation(String worldName, double x, double y, double z, float yaw, float pitch, boolean present) {
    public static MigrationLocation unknown() {
        return new MigrationLocation("", 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false);
    }
}
