package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.List;

public record AddonCertificationReport(
    String addonId,
    String certificationLevel,
    ApiContractVerification contractVerification,
    List<AddonCertificationCheck> checks
) {
    public AddonCertificationReport {
        addonId = addonId == null || addonId.isBlank() ? "unknown-addon" : addonId;
        certificationLevel = certificationLevel == null || certificationLevel.isBlank() ? "uncertified" : certificationLevel;
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public boolean certified() {
        return requiredFailures().isEmpty();
    }

    public List<AddonCertificationCheck> requiredFailures() {
        return checks.stream()
            .filter(AddonCertificationCheck::required)
            .filter(check -> !check.passed())
            .toList();
    }

    public List<AddonCertificationCheck> advisoryFailures() {
        return checks.stream()
            .filter(check -> !check.required())
            .filter(check -> !check.passed())
            .toList();
    }

    public List<String> failureSummary() {
        List<String> failures = new ArrayList<>();
        requiredFailures().forEach(check -> failures.add(check.id() + ":" + check.detail()));
        advisoryFailures().forEach(check -> failures.add("advisory:" + check.id() + ":" + check.detail()));
        return List.copyOf(failures);
    }

    public void requireCertified() {
        if (!certified()) {
            throw new IllegalStateException("CloudIslands addon certification failed for " + addonId + ": " + String.join("; ", failureSummary()));
        }
    }
}
