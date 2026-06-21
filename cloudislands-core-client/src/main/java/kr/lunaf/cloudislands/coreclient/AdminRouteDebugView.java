package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record AdminRouteDebugView(List<AdminRouteSessionView> sessions, List<AdminRouteTicketView> tickets) {
    public AdminRouteDebugView {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        tickets = tickets == null ? List.of() : List.copyOf(tickets);
    }
}
