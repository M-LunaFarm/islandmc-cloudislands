package kr.lunaf.cloudislands.common.observability;

import java.util.List;

public record ProductionRunbookStep(
    String gate,
    String actionCommand,
    String verificationCommand,
    String rollbackCommand,
    List<String> requiredEvidence,
    List<String> failureInjections
) {
    public ProductionRunbookStep {
        gate = normalize(gate);
        actionCommand = normalize(actionCommand);
        verificationCommand = normalize(verificationCommand);
        rollbackCommand = normalize(rollbackCommand);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        failureInjections = failureInjections == null ? List.of() : List.copyOf(failureInjections);
    }

    public boolean actionable() {
        return !gate.isBlank()
            && !actionCommand.isBlank()
            && !verificationCommand.isBlank()
            && !rollbackCommand.isBlank()
            && !requiredEvidence.isEmpty();
    }

    public String summary() {
        return gate + "|action=" + actionCommand + "|verify=" + verificationCommand + "|rollback=" + rollbackCommand;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
