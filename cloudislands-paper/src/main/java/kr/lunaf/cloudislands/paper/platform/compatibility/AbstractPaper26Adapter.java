package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractPaper26Adapter implements PaperVersionAdapter {
    private final String adapterId;
    private final VersionRange supportedRange;
    private final RuntimeCapabilities capabilities;

    AbstractPaper26Adapter(String adapterId, int minorVersion) {
        this.adapterId = adapterId;
        this.supportedRange = VersionRange.majorMinor(adapterId, 26, minorVersion);
        this.capabilities = RuntimeCapabilities.baseline();
    }

    @Override
    public final String adapterId() {
        return adapterId;
    }

    @Override
    public final VersionRange supportedRange() {
        return supportedRange;
    }

    @Override
    public final RuntimeCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public final PaperAdapterSelfTest startupSelfTest(PaperCapabilities detectedCapabilities) {
        PaperCapabilities detected = detectedCapabilities == null ? new DetectedPaperCapabilities() : detectedCapabilities;
        List<String> requiredFailures = new ArrayList<>();
        if (!detected.supportsMinorApiVersion()) {
            requiredFailures.add("paper-minor-api-version");
        }
        if (!capabilities.completeForIslandNode()) {
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
        return new PaperAdapterSelfTest(adapterId, requiredFailures.isEmpty(), requiredFailures, optionalWarnings);
    }
}
