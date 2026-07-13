package kr.lunaf.cloudislands.coreservice.http;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryCoreIdempotencyStore implements CoreIdempotencyStore {
    private final Map<String, Entry> entries = new HashMap<>();

    @Override
    public synchronized BeginResult begin(String key, String requestFingerprint) {
        Entry existing = entries.get(key);
        if (existing == null) {
            entries.put(key, new Entry(requestFingerprint, null));
            return BeginResult.owner();
        }
        if (!existing.requestFingerprint().equals(requestFingerprint)) {
            return BeginResult.conflict();
        }
        return existing.response() == null
            ? BeginResult.inProgress()
            : BeginResult.replay(existing.response());
    }

    @Override
    public synchronized void complete(String key, String requestFingerprint, StoredResponse response) {
        Entry existing = entries.get(key);
        if (existing == null || !existing.requestFingerprint().equals(requestFingerprint)) {
            throw new IllegalStateException("idempotency claim no longer belongs to this request");
        }
        entries.put(key, new Entry(requestFingerprint, response));
    }

    private record Entry(String requestFingerprint, StoredResponse response) {
    }
}
