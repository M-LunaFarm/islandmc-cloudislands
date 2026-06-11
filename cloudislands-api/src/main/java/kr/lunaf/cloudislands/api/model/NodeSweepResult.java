package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record NodeSweepResult(List<String> nodes, int recoveryRequired) {}
