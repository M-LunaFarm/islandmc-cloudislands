package kr.lunaf.cloudislands.testkit;

import java.util.List;

public record PublicApiSignatureReport(
    String baselineResource,
    int baselineSignatureCount,
    int currentSignatureCount,
    List<String> missingSignatures
) {
    public PublicApiSignatureReport {
        baselineResource = baselineResource == null || baselineResource.isBlank() ? "unknown" : baselineResource;
        baselineSignatureCount = Math.max(0, baselineSignatureCount);
        currentSignatureCount = Math.max(0, currentSignatureCount);
        missingSignatures = missingSignatures == null ? List.of() : List.copyOf(missingSignatures);
    }

    public boolean compatible() {
        return missingSignatures.isEmpty();
    }

    public String status() {
        return compatible() ? "compatible" : "missing-public-api-signatures";
    }
}
