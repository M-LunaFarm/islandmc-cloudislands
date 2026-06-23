package kr.lunaf.cloudislands.paper.platform.compatibility;

import org.bukkit.Bukkit;

public final class PaperRuntimeCompatibility {
    private PaperRuntimeCompatibility() {
    }

    public static RuntimeSelection selectCurrent(PaperVersionAdapterRegistry registry) {
        return select(Bukkit.getMinecraftVersion(), Bukkit.getVersion(), registry);
    }

    public static RuntimeSelection select(String minecraftVersion, String serverVersion, PaperVersionAdapterRegistry registry) {
        return select(minecraftVersion, serverVersion, registry, new DetectedPaperCapabilities());
    }

    static RuntimeSelection select(
        String minecraftVersion,
        String serverVersion,
        PaperVersionAdapterRegistry registry,
        PaperCapabilities detectedCapabilities
    ) {
        if (registry == null) {
            throw new IllegalArgumentException("Paper adapter registry is required");
        }
        String rawVersion = preferredRuntimeVersion(minecraftVersion, serverVersion);
        ServerVersion version = ServerVersion.parse(rawVersion);
        PaperVersionAdapter adapter = registry.select(version);
        PaperAdapterSelfTest selfTest = adapter.startupSelfTest(detectedCapabilities);
        if (!selfTest.passed()) {
            throw new IllegalStateException(
                "Paper adapter startup self-test failed original=" + version.original()
                    + " normalized=" + version.normalized()
                    + " adapter=" + adapter.adapterId()
                    + " requiredFailures=" + String.join(",", selfTest.requiredFailures())
            );
        }
        return new RuntimeSelection(version, adapter, registry.supportedRangeSummary(), selfTest);
    }

    private static String preferredRuntimeVersion(String minecraftVersion, String serverVersion) {
        String minecraft = minecraftVersion == null ? "" : minecraftVersion.trim();
        if (!minecraft.isBlank()) {
            return minecraft;
        }
        String server = serverVersion == null ? "" : serverVersion.trim();
        if (!server.isBlank()) {
            return server;
        }
        return "";
    }

    public record RuntimeSelection(
        ServerVersion version,
        PaperVersionAdapter adapter,
        String supportedRanges,
        PaperAdapterSelfTest selfTest
    ) {
        public RuntimeSelection {
            if (version == null) {
                throw new IllegalArgumentException("runtime version is required");
            }
            if (adapter == null) {
                throw new IllegalArgumentException("Paper adapter is required");
            }
            supportedRanges = supportedRanges == null ? "" : supportedRanges;
            selfTest = selfTest == null ? PaperAdapterSelfTest.passed(adapter.adapterId()) : selfTest;
        }

        public String adapterId() {
            return adapter.adapterId();
        }

        public RuntimeCapabilities capabilities() {
            return adapter.capabilities();
        }

        public String diagnosticsSection() {
            return "## runtime-compatibility\n"
                + "paperVersionOriginal=" + version.original() + '\n'
                + "paperVersionNormalized=" + version.normalized() + '\n'
                + "paperVersionStable=" + version.stable() + '\n'
                + "paperAdapterId=" + adapter.adapterId() + '\n'
                + "paperAdapterRange=" + adapter.supportedRange().summary() + '\n'
                + "paperSupportedRanges=" + supportedRanges + '\n'
                + "paperAdapterCapabilities=" + adapter.capabilities().summary() + '\n'
                + "paperAdapterSelfTest=" + selfTest.summary() + '\n';
        }
    }
}
