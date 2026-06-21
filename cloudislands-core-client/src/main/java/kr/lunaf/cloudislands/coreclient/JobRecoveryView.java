package kr.lunaf.cloudislands.coreclient;

public record JobRecoveryView(boolean accepted, String recovered, String code) {
    public JobRecoveryView {
        recovered = recovered == null ? "" : recovered;
        code = code == null ? "" : code;
    }
}
