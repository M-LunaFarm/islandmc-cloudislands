package kr.lunaf.cloudislands.api.model;

import java.util.Locale;

public record RoleId(String value) {
    public RoleId {
        value = normalizeRaw(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("role id is required");
        }
    }

    public static RoleId of(String value) {
        return new RoleId(value);
    }

    public static RoleId of(String value, String fallback) {
        String normalized = normalizeRaw(value);
        if (normalized.isBlank()) {
            normalized = normalizeRaw(fallback);
        }
        return new RoleId(normalized);
    }

    public static RoleId of(IslandRole role, String fallback) {
        return of(role == null ? "" : role.name(), fallback);
    }

    public static String normalize(String value, String fallback) {
        return of(value, fallback).value();
    }

    public boolean systemRole() {
        return SystemRole.isSystemRole(this);
    }

    public SystemRole asSystemRole() {
        return SystemRole.from(this);
    }

    private static String normalizeRaw(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
