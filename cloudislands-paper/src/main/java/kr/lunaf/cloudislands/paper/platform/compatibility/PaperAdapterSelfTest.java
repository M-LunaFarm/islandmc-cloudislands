package kr.lunaf.cloudislands.paper.platform.compatibility;

import java.util.List;

public record PaperAdapterSelfTest(
    String adapterId,
    boolean passed,
    List<String> requiredFailures,
    List<String> optionalWarnings
) {
    public PaperAdapterSelfTest {
        if (adapterId == null || adapterId.isBlank()) {
            throw new IllegalArgumentException("adapter id is required");
        }
        requiredFailures = requiredFailures == null ? List.of() : List.copyOf(requiredFailures);
        optionalWarnings = optionalWarnings == null ? List.of() : List.copyOf(optionalWarnings);
        if (passed && !requiredFailures.isEmpty()) {
            throw new IllegalArgumentException("passed self-test cannot include required failures");
        }
    }

    public static PaperAdapterSelfTest passed(String adapterId) {
        return new PaperAdapterSelfTest(adapterId, true, List.of(), List.of());
    }

    public String summary() {
        return "passed=" + passed
            + ",requiredFailures=" + String.join("|", requiredFailures)
            + ",optionalWarnings=" + String.join("|", optionalWarnings);
    }
}
