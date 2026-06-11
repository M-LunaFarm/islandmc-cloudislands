package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record MigrationRunSnapshot(
    String state,
    String path,
    int manifests,
    boolean canImport,
    boolean imported,
    int importedIslands,
    boolean passed,
    int expected,
    boolean rolledBack,
    int removedIslands,
    List<MigrationIssueSnapshot> issues
) {}
