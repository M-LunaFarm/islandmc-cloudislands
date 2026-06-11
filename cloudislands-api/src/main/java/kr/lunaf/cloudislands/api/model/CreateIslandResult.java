package kr.lunaf.cloudislands.api.model;

public record CreateIslandResult(boolean accepted, String code, IslandSnapshot island, RouteTicket ticket) {}
