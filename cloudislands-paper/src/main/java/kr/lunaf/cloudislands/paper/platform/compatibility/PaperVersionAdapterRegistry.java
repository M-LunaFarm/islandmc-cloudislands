package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaperVersionAdapterRegistry {
    private final List<PaperVersionAdapter> adapters;

    public PaperVersionAdapterRegistry(Collection<? extends PaperVersionAdapter> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("at least one Paper adapter is required");
        }
        this.adapters = List.copyOf(adapters);
        validateUniqueAdapters(this.adapters);
    }

    public static PaperVersionAdapterRegistry defaults() {
        RuntimeCapabilities baseline = RuntimeCapabilities.baseline();
        return new PaperVersionAdapterRegistry(List.of(
            new Paper121FamilyAdapter(),
            new Paper261Adapter(),
            new DefaultPaperVersionAdapter("paper-26.2", VersionRange.majorMinor("paper-26.2", 26, 2), baseline)
        ));
    }

    public PaperVersionAdapter select(String rawVersion) {
        ServerVersion version = ServerVersion.parse(rawVersion);
        return select(version);
    }

    public PaperVersionAdapter select(ServerVersion version) {
        List<PaperVersionAdapter> matches = adapters.stream()
            .filter(adapter -> adapter.supports(version))
            .toList();
        if (matches.isEmpty()) {
            throw new UnsupportedPaperVersionException(
                "Unsupported Paper version original=" + version.original()
                    + " normalized=" + version.normalized()
                    + " supported=" + supportedRangeSummary()
            );
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                "Duplicate Paper adapters for original=" + version.original()
                    + " normalized=" + version.normalized()
                    + " adapters=" + matches.stream().map(PaperVersionAdapter::adapterId).collect(Collectors.joining(","))
            );
        }
        PaperVersionAdapter adapter = matches.get(0);
        if (!adapter.capabilities().completeForIslandNode()) {
            throw new IllegalStateException(
                "Paper adapter lacks island-node capabilities original=" + version.original()
                    + " normalized=" + version.normalized()
                    + " adapter=" + adapter.adapterId()
            );
        }
        return adapter;
    }

    public List<PaperVersionAdapter> adapters() {
        return adapters;
    }

    public String supportedRangeSummary() {
        return adapters.stream()
            .map(adapter -> adapter.adapterId() + ":" + adapter.supportedRange().summary())
            .collect(Collectors.joining(","));
    }

    private static void validateUniqueAdapters(List<PaperVersionAdapter> adapters) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> ranges = new LinkedHashSet<>();
        List<String> duplicateIds = new ArrayList<>();
        List<String> duplicateRanges = new ArrayList<>();
        for (PaperVersionAdapter adapter : adapters) {
            if (!ids.add(adapter.adapterId())) {
                duplicateIds.add(adapter.adapterId());
            }
            String range = adapter.supportedRange().major() + "." + adapter.supportedRange().minor();
            if (!ranges.add(range)) {
                duplicateRanges.add(range);
            }
        }
        if (!duplicateIds.isEmpty() || !duplicateRanges.isEmpty()) {
            throw new IllegalArgumentException("Duplicate Paper adapters ids=" + duplicateIds + " ranges=" + duplicateRanges);
        }
    }
}
