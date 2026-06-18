package kr.lunaf.cloudislands.common.feature;

import java.util.List;
import java.util.Locale;

public final class SatisFeaturePackActivationPolicy {
    public static final String ACTIVATION_POLICY = "external-addon-and-built-in-compatible-use-same-root-gate-feature-gate-and-cloudislands-api-checks";
    public static final String EXTERNAL_DESCRIPTOR_POLICY = "external-addon-runtime-requires-cloudislands-addon-descriptor";
    public static final String BUILT_IN_DESCRIPTOR_POLICY = "built-in-compatible-runtime-does-not-require-external-addon-jar-but-keeps-addon-spi-gates";
    public static final String DISABLED_POLICY = "disabled-mode-registers-no-satis-runtime-components";

    public static final String MODE_EXTERNAL_ADDON = "EXTERNAL_ADDON";
    public static final String MODE_BUILT_IN_COMPATIBLE = "BUILT_IN_COMPATIBLE";
    public static final String MODE_DISABLED = "DISABLED";

    private static final List<String> SUPPORTED_MODES = List.of(
        MODE_EXTERNAL_ADDON,
        MODE_BUILT_IN_COMPATIBLE,
        MODE_DISABLED
    );

    private SatisFeaturePackActivationPolicy() {
    }

    public static List<String> supportedModes() {
        return SUPPORTED_MODES;
    }

    public static ActivationDecision decide(
        String mode,
        boolean rootEnabled,
        boolean featureEnabled,
        boolean cloudIslandsApiAvailable,
        boolean externalAddonDescriptorPresent
    ) {
        String normalizedMode = normalizeMode(mode);
        if (!SUPPORTED_MODES.contains(normalizedMode)) {
            return ActivationDecision.blocked(normalizedMode, "unsupported-mode");
        }
        if (MODE_DISABLED.equals(normalizedMode)) {
            return ActivationDecision.blocked(normalizedMode, "mode-disabled");
        }
        if (!rootEnabled) {
            return ActivationDecision.blocked(normalizedMode, "root-disabled");
        }
        if (!featureEnabled) {
            return ActivationDecision.blocked(normalizedMode, "feature-disabled");
        }
        if (!cloudIslandsApiAvailable) {
            return ActivationDecision.blocked(normalizedMode, "cloudislands-api-missing");
        }
        if (MODE_EXTERNAL_ADDON.equals(normalizedMode) && !externalAddonDescriptorPresent) {
            return ActivationDecision.blocked(normalizedMode, "external-addon-descriptor-missing");
        }
        return ActivationDecision.enabled(normalizedMode, runtimeShape(normalizedMode));
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_EXTERNAL_ADDON;
        }
        String normalized = mode.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
        if (normalized.equals("ADDON") || normalized.equals("EXTERNAL") || normalized.equals("EXTERNAL_PLUGIN")) {
            return MODE_EXTERNAL_ADDON;
        }
        if (normalized.equals("BUILTIN") || normalized.equals("BUILT_IN") || normalized.equals("BUILT_IN_FEATURE_PACK")) {
            return MODE_BUILT_IN_COMPATIBLE;
        }
        if (normalized.equals("OFF") || normalized.equals("FALSE")) {
            return MODE_DISABLED;
        }
        return normalized;
    }

    public static String runtimeShape(String mode) {
        String normalizedMode = normalizeMode(mode);
        if (MODE_EXTERNAL_ADDON.equals(normalizedMode)) {
            return "external-addon-runtime";
        }
        if (MODE_BUILT_IN_COMPATIBLE.equals(normalizedMode)) {
            return "built-in-compatible-runtime";
        }
        return "no-runtime";
    }

    public record ActivationDecision(boolean runtimeEnabled, String mode, String runtimeShape, String blockReason) {
        private static ActivationDecision enabled(String mode, String runtimeShape) {
            return new ActivationDecision(true, mode, runtimeShape, "none");
        }

        private static ActivationDecision blocked(String mode, String blockReason) {
            return new ActivationDecision(false, mode, "no-runtime", blockReason);
        }
    }
}
