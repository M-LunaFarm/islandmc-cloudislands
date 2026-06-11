package kr.lunaf.cloudislands.api.model;

public record MigrationIssueSnapshot(String code, String message, boolean blocking) {}
