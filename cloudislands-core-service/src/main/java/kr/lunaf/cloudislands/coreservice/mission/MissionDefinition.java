package kr.lunaf.cloudislands.coreservice.mission;

public record MissionDefinition(String missionKey, String kind, String title, long goal, String reward) {}
