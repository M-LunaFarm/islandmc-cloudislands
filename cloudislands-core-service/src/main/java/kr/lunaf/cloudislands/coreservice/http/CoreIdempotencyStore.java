package kr.lunaf.cloudislands.coreservice.http;

/** Shared response ledger for Core mutation requests carrying an Idempotency-Key. */
public interface CoreIdempotencyStore {
    BeginResult begin(String key, String requestFingerprint);

    void complete(String key, String requestFingerprint, StoredResponse response);

    enum BeginStatus {
        OWNER,
        REPLAY,
        CONFLICT,
        IN_PROGRESS
    }

    record BeginResult(BeginStatus status, StoredResponse response) {
        public BeginResult {
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
        }

        public static BeginResult owner() {
            return new BeginResult(BeginStatus.OWNER, null);
        }

        public static BeginResult replay(StoredResponse response) {
            return new BeginResult(BeginStatus.REPLAY, response);
        }

        public static BeginResult conflict() {
            return new BeginResult(BeginStatus.CONFLICT, null);
        }

        public static BeginResult inProgress() {
            return new BeginResult(BeginStatus.IN_PROGRESS, null);
        }
    }

    record StoredResponse(int status, String contentType, String body) {
        public StoredResponse {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("status must be a valid HTTP status");
            }
            contentType = contentType == null || contentType.isBlank()
                ? "application/json; charset=utf-8"
                : contentType;
            body = body == null ? "" : body;
        }
    }
}
