package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.compat.ApiCompatibilityDecision;

public record ApiContractVerification(
    String subject,
    String requestedApiVersion,
    String runtimeApiVersion,
    ApiCompatibilityDecision apiCompatibility,
    String contractMetadataStatus,
    List<String> missingContractMetadataKeys,
    List<String> missingAddonMetadataKeys
) {
    public ApiContractVerification {
        subject = subject == null || subject.isBlank() ? "unknown" : subject;
        requestedApiVersion = requestedApiVersion == null || requestedApiVersion.isBlank() ? "unknown" : requestedApiVersion;
        runtimeApiVersion = runtimeApiVersion == null || runtimeApiVersion.isBlank() ? "unknown" : runtimeApiVersion;
        contractMetadataStatus = contractMetadataStatus == null || contractMetadataStatus.isBlank() ? "missing-metadata" : contractMetadataStatus;
        missingContractMetadataKeys = missingContractMetadataKeys == null ? List.of() : List.copyOf(missingContractMetadataKeys);
        missingAddonMetadataKeys = missingAddonMetadataKeys == null ? List.of() : List.copyOf(missingAddonMetadataKeys);
    }

    public boolean passed() {
        return apiCompatible()
            && "compatible".equals(contractMetadataStatus)
            && missingContractMetadataKeys.isEmpty()
            && missingAddonMetadataKeys.isEmpty();
    }

    public boolean apiCompatible() {
        return apiCompatibility != null && apiCompatibility.compatible();
    }

    public List<String> failures() {
        List<String> failures = new ArrayList<>();
        if (!apiCompatible()) {
            String status = apiCompatibility == null ? "missing-decision" : apiCompatibility.status().code();
            String reason = apiCompatibility == null ? "api compatibility was not evaluated" : apiCompatibility.reason();
            failures.add("api-compatibility:" + status + ":" + reason);
        }
        if (!"compatible".equals(contractMetadataStatus)) {
            failures.add("contract-metadata:" + contractMetadataStatus);
        }
        if (!missingContractMetadataKeys.isEmpty()) {
            failures.add("missing-contract-metadata:" + String.join(",", missingContractMetadataKeys));
        }
        if (!missingAddonMetadataKeys.isEmpty()) {
            failures.add("missing-addon-metadata:" + String.join(",", missingAddonMetadataKeys));
        }
        return List.copyOf(failures);
    }

    public void requirePassed() {
        if (!passed()) {
            throw new IllegalStateException("CloudIslands API contract verification failed for " + subject + ": " + String.join("; ", failures()));
        }
    }
}
