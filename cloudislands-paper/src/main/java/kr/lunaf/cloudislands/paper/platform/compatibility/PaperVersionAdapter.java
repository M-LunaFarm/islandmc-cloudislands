package kr.lunaf.cloudislands.paper.platform.compatibility;

public interface PaperVersionAdapter {
    String adapterId();

    VersionRange supportedRange();

    RuntimeCapabilities capabilities();

    default boolean supports(ServerVersion version) {
        return supportedRange().includes(version);
    }
}
