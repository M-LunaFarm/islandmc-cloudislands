package kr.lunaf.cloudislands.testkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class ApiCompatibilityCheckCli {
    private ApiCompatibilityCheckCli() {
    }

    public static void main(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        ApiContractVerification verification = ApiContractVerifier.verifyRuntimeMetadata(CloudIslandsApiContract.metadata());
        PublicApiSignatureReport signatureReport = PublicApiSignatureVerifier.verifyBaseline();
        String json = reportJson(verification, signatureReport);
        System.out.println(json);
        if (options.reportOut() != null) {
            Files.createDirectories(options.reportOut().toAbsolutePath().getParent());
            Files.writeString(options.reportOut(), json + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        verification.requirePassed();
        if (!signatureReport.compatible()) {
            throw new IllegalStateException("CloudIslands public API signature verification failed: " + signatureReport.missingSignatures());
        }
    }

    static String reportJson(ApiContractVerification verification) {
        return reportJson(verification, PublicApiSignatureVerifier.verifyBaseline());
    }

    static String reportJson(ApiContractVerification verification, PublicApiSignatureReport signatureReport) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        boolean signatureCompatible = signatureReport != null && signatureReport.compatible();
        root.put("subject", verification.subject());
        root.put("passed", verification.passed() && signatureCompatible);
        root.put("requestedApiVersion", verification.requestedApiVersion());
        root.put("runtimeApiVersion", verification.runtimeApiVersion());
        root.put("apiCompatibilityStatus", verification.apiCompatibility().status().code());
        root.put("apiCompatibilityReason", verification.apiCompatibility().reason());
        root.put("contractMetadataStatus", verification.contractMetadataStatus());
        root.put("missingContractMetadataKeys", verification.missingContractMetadataKeys());
        root.put("missingAddonMetadataKeys", verification.missingAddonMetadataKeys());
        root.put("publicApiSignatureStatus", signatureReport == null ? "missing-signature-report" : signatureReport.status());
        root.put("publicApiSignatureBaseline", signatureReport == null ? "" : signatureReport.baselineResource());
        root.put("publicApiBaselineSignatureCount", signatureReport == null ? 0 : signatureReport.baselineSignatureCount());
        root.put("publicApiCurrentSignatureCount", signatureReport == null ? 0 : signatureReport.currentSignatureCount());
        root.put("missingPublicApiSignatures", signatureReport == null ? java.util.List.of("missing-signature-report") : signatureReport.missingSignatures());
        java.util.ArrayList<String> failures = new java.util.ArrayList<>(verification.failures());
        if (!signatureCompatible) {
            failures.add("public-api-signatures:" + (signatureReport == null ? "missing-signature-report" : String.join(",", signatureReport.missingSignatures())));
        }
        root.put("failures", java.util.List.copyOf(failures));
        LinkedHashMap<String, Object> policy = new LinkedHashMap<>();
        policy.put("semanticVersionPolicy", CloudIslandsApiContract.SEMANTIC_VERSION_POLICY);
        policy.put("deprecationPolicy", CloudIslandsApiContract.DEPRECATION_POLICY);
        policy.put("compatibilityTestkitPolicy", CloudIslandsApiContract.COMPATIBILITY_TESTKIT_POLICY);
        root.put("releaseGatePolicy", policy);
        return SimpleJson.stringify(root);
    }

    private record CliOptions(Path reportOut) {
        private static CliOptions parse(String[] args) {
            Path reportOut = null;
            for (int index = 0; index < (args == null ? 0 : args.length); index++) {
                String arg = args[index];
                switch (arg) {
                    case "--report-out" -> reportOut = Path.of(next(args, ++index, arg));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new CliOptions(reportOut);
        }

        private static String next(String[] args, int index, String flag) {
            if (args == null || index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }
}
