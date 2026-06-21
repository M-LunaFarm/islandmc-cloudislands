package kr.lunaf.cloudislands.coreclient;

public record BankMutationView(String body, boolean accepted, String code, String balance) {
    public BankMutationView {
        body = body == null ? "" : body;
        code = code == null ? "" : code;
        balance = balance == null || balance.isBlank() ? "0" : balance;
    }
}
