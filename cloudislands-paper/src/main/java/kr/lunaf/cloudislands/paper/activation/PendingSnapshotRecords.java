package kr.lunaf.cloudislands.paper.activation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PendingSnapshotRecords {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Map<RecordKey, PendingSnapshotRecord> pending = new HashMap<>();
    private final Map<UUID, PendingSnapshotRecord> inFlight = new HashMap<>();
    private final Path journalPath;
    private String lastPersistenceError = "";
    private int discardedJournalRecords;
    private boolean journalReadable = true;

    PendingSnapshotRecords() {
        this(null);
    }

    PendingSnapshotRecords(Path journalPath) {
        this.journalPath = journalPath;
        loadJournal();
    }

    synchronized boolean enqueue(PendingSnapshotRecord record) {
        PendingSnapshotRecord existing = pending.putIfAbsent(RecordKey.of(record), record);
        return existing != null || persist();
    }

    synchronized boolean contains(UUID islandId) {
        return pending.keySet().stream().anyMatch(key -> key.islandId().equals(islandId));
    }

    synchronized List<PendingSnapshotRecord> claimAll() {
        return pending.values().stream()
            .sorted(Comparator.comparing(PendingSnapshotRecord::islandId).thenComparingLong(PendingSnapshotRecord::snapshotNo))
            .filter(record -> record.equals(oldestPending(record.islandId())))
            .filter(this::claim)
            .toList();
    }

    synchronized List<PendingSnapshotRecord> claim(UUID islandId) {
        PendingSnapshotRecord record = oldestPending(islandId);
        return record != null && claim(record) ? List.of(record) : List.of();
    }

    synchronized boolean completed(PendingSnapshotRecord record) {
        inFlight.remove(record.islandId(), record);
        RecordKey key = RecordKey.of(record);
        if (!pending.remove(key, record)) {
            return true;
        }
        if (persist()) {
            return true;
        }
        pending.putIfAbsent(key, record);
        return false;
    }

    synchronized void failed(PendingSnapshotRecord record) {
        inFlight.remove(record.islandId(), record);
    }

    synchronized int size() {
        return pending.size();
    }

    synchronized String lastPersistenceError() {
        return lastPersistenceError;
    }

    synchronized int discardedJournalRecords() {
        return discardedJournalRecords;
    }

    private boolean claim(PendingSnapshotRecord record) {
        return inFlight.putIfAbsent(record.islandId(), record) == null;
    }

    private PendingSnapshotRecord oldestPending(UUID islandId) {
        return pending.values().stream()
            .filter(record -> record.islandId().equals(islandId))
            .min(Comparator.comparingLong(PendingSnapshotRecord::snapshotNo))
            .orElse(null);
    }

    private void loadJournal() {
        if (journalPath == null || Files.notExists(journalPath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(journalPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    PendingSnapshotRecord record = decode(line);
                    pending.putIfAbsent(RecordKey.of(record), record);
                } catch (RuntimeException invalidRecord) {
                    discardedJournalRecords++;
                }
            }
        } catch (IOException error) {
            lastPersistenceError = errorMessage(error);
            journalReadable = false;
        }
    }

    private boolean persist() {
        if (journalPath == null) {
            lastPersistenceError = "";
            return true;
        }
        if (!journalReadable) {
            return false;
        }
        Path parent = journalPath.toAbsolutePath().getParent();
        Path temporary = journalPath.resolveSibling(journalPath.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = pending.values().stream()
                .sorted(Comparator.comparing((PendingSnapshotRecord record) -> record.islandId().toString()).thenComparingLong(PendingSnapshotRecord::snapshotNo))
                .map(PendingSnapshotRecords::encode)
                .toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, journalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, journalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            lastPersistenceError = "";
            return true;
        } catch (IOException error) {
            lastPersistenceError = errorMessage(error);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The next persistence attempt replaces the same temporary file.
            }
            return false;
        }
    }

    private static String encode(PendingSnapshotRecord record) {
        return String.join("\t",
            record.islandId().toString(),
            Long.toString(record.snapshotNo()),
            encodeText(record.storagePath()),
            encodeText(record.reason()),
            encodeText(record.checksum()),
            Long.toString(record.sizeBytes()),
            encodeText(record.nodeId()),
            Long.toString(record.fencingToken())
        );
    }

    private static PendingSnapshotRecord decode(String line) {
        String[] values = line.split("\t", -1);
        if (values.length != 8) {
            throw new IllegalArgumentException("invalid pending snapshot journal record");
        }
        return new PendingSnapshotRecord(
            UUID.fromString(values[0]),
            Long.parseLong(values[1]),
            decodeText(values[2]),
            decodeText(values[3]),
            decodeText(values[4]),
            Long.parseLong(values[5]),
            decodeText(values[6]),
            Long.parseLong(values[7])
        );
    }

    private static String encodeText(String value) {
        return ENCODER.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static String errorMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record RecordKey(UUID islandId, long snapshotNo) {
        private static RecordKey of(PendingSnapshotRecord record) {
            return new RecordKey(record.islandId(), record.snapshotNo());
        }
    }

    record PendingSnapshotRecord(
        UUID islandId,
        long snapshotNo,
        String storagePath,
        String reason,
        String checksum,
        long sizeBytes,
        String nodeId,
        long fencingToken
    ) {}
}
