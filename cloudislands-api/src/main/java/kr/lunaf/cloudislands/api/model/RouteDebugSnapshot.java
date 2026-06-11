package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record RouteDebugSnapshot(List<PlayerRouteSessionSnapshot> sessions, List<RouteTicket> tickets) {}
