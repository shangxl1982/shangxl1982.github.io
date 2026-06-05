package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.journal.JournalEntry;
import org.hyperkv.lsmplus.journal.JournalReplayHandler;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RecoveryHandler implements JournalReplayHandler {

    private static final Logger log = LoggerFactory.getLogger(RecoveryHandler.class);

    private final MemoryTableManager memoryTableManager;
    private int recoveredEntries;

    public RecoveryHandler(MemoryTableManager memoryTableManager) {
        if (memoryTableManager == null) {
            throw new IllegalArgumentException("memoryTableManager must not be null");
        }
        this.memoryTableManager = memoryTableManager;
        this.recoveredEntries = 0;
    }

    @Override
    public void handle(JournalEntry entry, JournalReplayPoint replayPoint) {
        OperationType type = entry.getOperationType();
        List<JournalEntry.KeyValuePair> entries = entry.getEntries();

        if (entries == null || entries.isEmpty()) {
            return;
        }

        log.trace("Recovering journal entry: type={}, entries={}", type, entries.size());

        for (JournalEntry.KeyValuePair pair : entries) {
            IndexKey key = pair.getKey();
            IndexValue value = pair.getValue();

            switch (type) {
                case PUT -> {
                    if (value != null && !value.isTombstone()) {
                        memoryTableManager.put(key, value, replayPoint);
                        recoveredEntries++;
                    }
                }
                case DELETE -> {
                    memoryTableManager.put(key, IndexValue.tombstone(), replayPoint);
                    recoveredEntries++;
                }
                case BATCH -> {
                    if (value != null) {
                        memoryTableManager.put(key, value, replayPoint);
                        recoveredEntries++;
                    }
                }
                default -> log.warn("Unknown operation type during recovery: {}", type);
            }
        }
    }

    public int getRecoveredEntries() {
        return recoveredEntries;
    }
}
