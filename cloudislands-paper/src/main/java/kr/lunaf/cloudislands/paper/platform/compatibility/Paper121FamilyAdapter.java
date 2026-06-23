package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.ArrayList;
import java.util.List;

public final class Paper121FamilyAdapter implements PaperVersionAdapter {
    private static final String ADAPTER_ID = "paper-1.21";
    private static final VersionRange SUPPORTED_RANGE = VersionRange.majorMinor(ADAPTER_ID, 1, 21);
    private static final RuntimeCapabilities RUNTIME_CAPABILITIES = RuntimeCapabilities.baseline();

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public VersionRange supportedRange() {
        return SUPPORTED_RANGE;
    }

    @Override
    public RuntimeCapabilities capabilities() {
        return RUNTIME_CAPABILITIES;
    }

    @Override
    public PaperAdapterSelfTest startupSelfTest(PaperCapabilities detectedCapabilities) {
        PaperCapabilities detected = detectedCapabilities == null ? new DetectedPaperCapabilities() : detectedCapabilities;
        List<String> requiredFailures = new ArrayList<>();
        if (!detected.supportsMinorApiVersion()) {
            requiredFailures.add("paper-minor-api-version");
        }
        if (!RUNTIME_CAPABILITIES.completeForIslandNode()) {
            requiredFailures.add("island-node-runtime-capabilities");
        }

        List<String> optionalWarnings = new ArrayList<>();
        if (!detected.supportsRegionScheduler()) {
            optionalWarnings.add("region-scheduler");
        }
        if (!detected.supportsDataComponents()) {
            optionalWarnings.add("data-components");
        }
        if (!detected.supportsDialogApi()) {
            optionalWarnings.add("dialog-api");
        }
        if (!detected.supportsRegistryMutation()) {
            optionalWarnings.add("registry-mutation");
        }
        return new PaperAdapterSelfTest(ADAPTER_ID, requiredFailures.isEmpty(), requiredFailures, optionalWarnings);
    }
}
