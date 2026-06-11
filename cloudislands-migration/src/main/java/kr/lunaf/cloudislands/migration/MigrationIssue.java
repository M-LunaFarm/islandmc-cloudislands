package kr.lunaf.cloudislands.migration;

public record MigrationIssue(String code, String message, boolean blocking) {}
