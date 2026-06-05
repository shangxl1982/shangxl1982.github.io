package org.hyperkv.lsmplus.journal;

public interface JournalReplayHandler {

    void handle(JournalEntry entry, JournalReplayPoint replayPoint);
}