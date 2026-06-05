package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KVStorePerformanceCounterTest {

    @Test
    void testPerformanceCountersAreRecorded(@TempDir Path tempDir) throws Exception {
        File dataDir = tempDir.toFile();
        
        KVStore kvStore = new KVStore(dataDir);
        kvStore.start();

        try {
            for (int i = 0; i < 10; i++) {
                IndexKey key = IndexKey.orderedBytes(("key" + i).getBytes(StandardCharsets.UTF_8));
                IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
                kvStore.put(key, value);
            }

            for (int i = 0; i < 5; i++) {
                IndexKey key = IndexKey.orderedBytes(("key" + i).getBytes(StandardCharsets.UTF_8));
                IndexValue retrieved = kvStore.get(key);
                assertNotNull(retrieved);
            }

            Thread.sleep(11000);

            File perfLog = new File(dataDir, "performance-counter.log");
            assertTrue(perfLog.exists(), "Performance counter log file should be created");

            String logContent = Files.readString(perfLog.toPath());
            assertTrue(logContent.contains("kvstore_put"), "Log should contain put counter");
            assertTrue(logContent.contains("kvstore_get"), "Log should contain get counter");

        } finally {
            kvStore.shutdown();
        }
    }

    @Test
    void testPerformanceCounterLogFileCreated(@TempDir Path tempDir) throws Exception {
        File dataDir = tempDir.toFile();
        
        KVStore kvStore = new KVStore(dataDir);
        kvStore.start();

        try {
            IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
            
            kvStore.put(key, value);
            
            IndexValue retrieved = kvStore.get(key);
            assertNotNull(retrieved);

            Thread.sleep(11000);

            File perfLog = new File(dataDir, "performance-counter.log");
            assertTrue(perfLog.exists(), "Performance counter log file should be created");
            
            String logContent = Files.readString(perfLog.toPath());
            assertNotNull(logContent);
            assertFalse(logContent.isEmpty());

        } finally {
            kvStore.shutdown();
        }
    }
}
