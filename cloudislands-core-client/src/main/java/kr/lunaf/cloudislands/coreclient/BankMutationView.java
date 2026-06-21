package kr.lunaf.cloudislands.coreclient;

public record BankMutationView(boolean accepted, String code, String islandId, String balance, String updatedAt) {
    public BankMutationView(boolean accepted, String code, String balance) {
        this(accepted, code, "", balance, "");
    }

    public BankMutationView {
        code = code == null ? "" : code;
        islandId = islandId == null ? "" : islandId;
        balance = balance == null || balance.isBlank() ? "0" : balance;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
