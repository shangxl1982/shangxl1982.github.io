package org.hyperkv.lsmplus.config;

import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        configManager = new ConfigManager();
    }

    @Test
    void testDefaultConfig() {
        assertTrue(configManager.contains("memoryTable.maxSize"));
        assertTrue(configManager.contains("journal.batchSize"));
        assertTrue(configManager.contains("bplustree.pageSize"));
    }

    @Test
    void testGetInt() {
        int value = configManager.getInt("journal.batchSize");
        assertEquals(100, value);
    }

    @Test
    void testGetIntWithDefault() {
        int value = configManager.getInt("nonexistent", 50);
        assertEquals(50, value);
    }

    @Test
    void testGetLong() {
        long value = configManager.getLong("memoryTable.maxSize");
        assertEquals(64 * 1024 * 1024, value);
    }

    @Test
    void testGetBoolean() {
        boolean value = configManager.getBoolean("monitoring.enabled");
        assertTrue(value);
    }

    @Test
    void testGetString() {
        String value = configManager.getString("backup.dir");
        assertEquals("backups", value);
    }

    @Test
    void testGetDouble() {
        double value = configManager.getDouble("gc.lowOccupancyThreshold");
        assertEquals(0.3, value, 0.01);
    }

    @Test
    void testSet() {
        configManager.set("test.key", 123);
        assertEquals(123, configManager.getInt("test.key"));
    }

    @Test
    void testConfigChangeListener() {
        final boolean[] changed = {false};
        configManager.registerListener("test.key", (key, oldValue, newValue) -> {
            changed[0] = true;
            assertEquals("test.key", key);
            assertNull(oldValue);
            assertEquals(42, newValue);
        });

        configManager.set("test.key", 42);
        assertTrue(changed[0]);
    }

    @Test
    void testValidateSuccess() {
        assertDoesNotThrow(() -> configManager.validate());
    }

    @Test
    void testValidateFailure() {
        configManager.set("memoryTable.maxSize", -1);
        assertThrows(KVStoreRuntimeException.class, () -> configManager.validate());
    }

    @Test
    void testLoadFromFile() throws IOException {
        File tempFile = File.createTempFile("config", ".properties");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("journal.batchSize=200\n");
            writer.write("monitoring.enabled=false\n");
        }

        configManager.loadFromFile(tempFile);

        assertEquals(200, configManager.getInt("journal.batchSize"));
        assertFalse(configManager.getBoolean("monitoring.enabled"));

        tempFile.delete();
    }

    @Test
    void testGetAll() {
        var all = configManager.getAll();
        assertNotNull(all);
        assertTrue(all.size() > 0);
    }
}
