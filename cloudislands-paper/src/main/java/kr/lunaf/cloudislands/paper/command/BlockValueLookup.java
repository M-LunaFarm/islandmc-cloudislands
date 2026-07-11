package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kr.lunaf.cloudislands.coreclient.BlockValueView;

final class BlockValueLookup {
    private BlockValueLookup() {
    }

    static Optional<BlockValueView> find(List<BlockValueView> values, String requestedMaterial) {
        String requested = normalize(requestedMaterial);
        if (requested.isBlank()) {
            return Optional.empty();
        }
        return (values == null ? List.<BlockValueView>of() : values).stream()
            .filter(value -> value != null && normalize(value.materialKey()).equals(requested))
            .findFirst();
    }

    static String normalize(String material) {
        if (material == null || material.isBlank()) {
            return "";
        }
        String normalized = material.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
