package kr.lunaf.cloudislands.migration;

public record MigrationPermission(String roleName, String permissionName, boolean allowed) {}
