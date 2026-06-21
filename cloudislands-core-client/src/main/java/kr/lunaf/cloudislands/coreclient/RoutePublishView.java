package kr.lunaf.cloudislands.coreclient;

public record RoutePublishView(boolean accepted, String code) {
    public RoutePublishView {
        code = code == null ? "" : code;
    }
}
