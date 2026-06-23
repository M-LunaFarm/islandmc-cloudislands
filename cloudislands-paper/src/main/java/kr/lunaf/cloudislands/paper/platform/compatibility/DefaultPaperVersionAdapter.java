package kr.lunaf.cloudislands.paper.platform.compatibility;

public record DefaultPaperVersionAdapter(
    String adapterId,
    VersionRange supportedRange,
    RuntimeCapabilities capabilities
) implements PaperVersionAdapter {
    public DefaultPaperVersionAdapter {
        if (adapterId == null || adapterId.isBlank()) {
            throw new IllegalArgumentException("adapter id is required");
        }
        if (supportedRange == null) {
            throw new IllegalArgumentException("supported range is required");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities are required");
        }
    }
}
