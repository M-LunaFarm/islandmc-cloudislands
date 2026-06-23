package kr.lunaf.cloudislands.coreservice.job;

public record JobCompletionResult(boolean replayed, String requestHash) {
}
