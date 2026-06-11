package kr.lunaf.cloudislands.api.model;

public enum NodeState {
    STARTING,
    WARMING,
    READY,
    SOFT_FULL,
    HARD_FULL,
    DRAINING,
    SHUTTING_DOWN,
    DOWN
}
