package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record RoutePlan(UUID islandId, String targetNode, String targetServerName, RouteAction action, boolean activationRequired) {}
