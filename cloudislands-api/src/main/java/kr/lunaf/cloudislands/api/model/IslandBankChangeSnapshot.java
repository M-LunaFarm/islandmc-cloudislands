package kr.lunaf.cloudislands.api.model;

public record IslandBankChangeSnapshot(boolean accepted, String code, IslandBankSnapshot bank) {}
