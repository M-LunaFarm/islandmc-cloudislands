package kr.lunaf.cloudislands.testkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class ClusterSmokeVerifierCli {
    private ClusterSmokeVerifierCli() {
    }

    public static void main(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        if (options.printFixture()) {
            printReport(ClusterSmokeVerifier.verify(ClusterSmokeVerifier.completeEvidenceFixture()), options.reportOut());
            return;
        }
        if (options.evidencePath() == null) {
            throw new IllegalArgumentException("--evidence is required unless --print-fixture is used");
        }
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(readEvidence(options.evidencePath()));
        printReport(report, options.reportOut());
        if (!options.allowPartial()) {
            report.requireCertified();
        }
    }

    static ClusterSmokeEvidence readEvidence(Path path) throws IOException {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(Files.readString(path, StandardCharsets.UTF_8)));
        ClusterSmokeEvidence.Builder builder = ClusterSmokeEvidence.builder();
        SimpleJson.list(root.get("components")).forEach(component -> builder.component(SimpleJson.text(component)));
        Map<?, ?> evidence = SimpleJson.object(root.containsKey("evidenceByGate") ? root.get("evidenceByGate") : root.get("evidence"));
        evidence.forEach((gate, values) -> builder.evidence(SimpleJson.text(gate), SimpleJson.list(values).stream().map(SimpleJson::text).toList()));
        SimpleJson.list(root.get("failureInjections")).forEach(failure -> builder.failureInjection(SimpleJson.text(failure)));
        Map<?, ?> failureInjectionEvidence = SimpleJson.object(root.get("failureInjectionEvidence"));
        failureInjectionEvidence.forEach((failure, values) -> builder.failureInjectionEvidence(
            SimpleJson.text(failure),
            SimpleJson.list(values).stream().map(SimpleJson::text).toList()
        ));
        SimpleJson.list(root.get("assertions")).forEach(assertion -> {
            Map<?, ?> assertionObject = SimpleJson.object(assertion);
            if ("passed".equalsIgnoreCase(SimpleJson.text(assertionObject.get("result")))) {
                builder.passedAssertion(SimpleJson.text(assertionObject.get("name")));
            }
        });
        SimpleJson.list(root.get("artifacts")).forEach(artifact -> {
            Map<?, ?> artifactObject = SimpleJson.object(artifact);
            builder.artifact(
                SimpleJson.text(artifactObject.get("path")),
                SimpleJson.text(artifactObject.get("sha256")),
                SimpleJson.number(artifactObject.get("lineStart")),
                SimpleJson.number(artifactObject.get("lineEnd"))
            );
        });
        return builder.build();
    }

    private static void printReport(ClusterSmokeReport report, Path reportOut) throws IOException {
        String json = reportJson(report);
        System.out.println(json);
        if (reportOut != null) {
            Files.createDirectories(reportOut.toAbsolutePath().getParent());
            Files.writeString(reportOut, json + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    static String reportJson(ClusterSmokeReport report) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("certificationLevel", report.certificationLevel());
        root.put("certified", report.certified());
        root.put("missingComponents", report.missingComponents());
        root.put("incompleteGates", report.incompleteGates());
        root.put("missingFailureInjections", report.missingFailureInjections());
        root.put("missingFailureInjectionEvidence", report.missingFailureInjectionEvidence());
        root.put("missingEvidenceByGate", report.missingEvidenceByGate());
        root.put("missingEvidenceSummary", report.missingEvidenceSummary());
        root.put("missingScenarioEvidence", report.missingScenarioEvidence());
        root.put("missingScenarioEvidenceSummary", report.missingScenarioEvidenceSummary());
        root.put("missingScenarioFailureInjections", report.missingScenarioFailureInjections());
        root.put("missingScenarioFailureInjectionSummary", report.missingScenarioFailureInjectionSummary());
        root.put("missingEvidenceLinks", report.missingEvidenceLinks());
        root.put("failures", report.failures());
        return SimpleJson.stringify(root);
    }

    private record CliOptions(Path evidencePath, Path reportOut, boolean allowPartial, boolean printFixture) {
        private static CliOptions parse(String[] args) {
            Path evidencePath = null;
            Path reportOut = null;
            boolean allowPartial = false;
            boolean printFixture = false;
            for (int index = 0; index < (args == null ? 0 : args.length); index++) {
                String arg = args[index];
                switch (arg) {
                    case "--evidence" -> evidencePath = Path.of(next(args, ++index, arg));
                    case "--report-out" -> reportOut = Path.of(next(args, ++index, arg));
                    case "--allow-partial" -> allowPartial = true;
                    case "--print-fixture" -> printFixture = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return new CliOptions(evidencePath, reportOut, allowPartial, printFixture);
        }

        private static String next(String[] args, int index, String flag) {
            if (args == null || index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }
}
