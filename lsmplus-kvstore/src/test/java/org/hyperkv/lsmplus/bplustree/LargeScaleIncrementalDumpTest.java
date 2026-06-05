package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.core.KVStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Repro for the "tree becomes invalid after incremental update at larger scale"
 * bug hit by DemoDataGenerator: v2's incremental dump of ~10k entries through a
 * freshly-recovered KVStore produces a tree that v3 cannot update.
 *
 * <p>Unlike the in-process dumper tests, this one restarts KVStore between
 * dumps so the second dump exercises the recovery path, matching
 * DemoDataGenerator's behavior.
 */
class LargeScaleIncrementalDumpTest {

    @TempDir
    File tempDir;

    private static String bigValue(int i) {
        return "value-" + String.format("%05d", i) + "-" + "x".repeat(50);
    }

    private static void insertBatch(KVStore store, int version, int count) {
        for (int i = 0; i < count; i++) {
            String k = "key-" + version + "-" + String.format("%05d", i);
            store.put(IndexKey.orderedBytes(k.getBytes()),
                      IndexValue.normal(bigValue(i).getBytes()));
        }
    }

    private static void sealAndDump(KVStore store) throws InterruptedException {
        store.sealActiveTable();
        store.triggerAsyncDump();
        while (store.isDumpInProgress()) {
            Thread.sleep(50);
        }
    }

    @Test
    void multipleRestartsAndIncrementalDumpsProduceValidTree() throws Exception {
        File dataDir = new File(tempDir, "kvstore");
        dataDir.mkdirs();
        int keysPerVersion = 2000;
        int versions = 3;

        for (int v = 1; v <= versions; v++) {
            KVStore store = new KVStore(dataDir);
            store.start();
            insertBatch(store, v, keysPerVersion);
            sealAndDump(store);
            store.shutdown();
        }

        // Final read-back: open one more time, check every key.
        KVStore store = new KVStore(dataDir);
        store.start();
        try {
            int missing = 0;
            for (int v = 1; v <= versions; v++) {
                for (int i = 0; i < keysPerVersion; i++) {
                    String k = "key-" + v + "-" + String.format("%05d", i);
                    IndexValue value = store.get(IndexKey.orderedBytes(k.getBytes()));
                    if (value == null) missing++;
                }
            }
            if (missing != 0) {
                fail("After " + versions + " dumps with restarts: missing "
                     + missing + "/" + (versions * keysPerVersion) + " keys");
            }
        } finally {
            store.shutdown();
        }
    }
}
