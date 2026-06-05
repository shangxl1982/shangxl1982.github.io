package org.hyperkv.lsmplus.config;

import org.hyperkv.lsmplus.exception.ErrorCode;
import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final Map<String, Object> config;
    private final Map<String, ConfigChangeListener> listeners;

    public ConfigManager() {
        this.config = new HashMap<>();
        this.listeners = new HashMap<>();
        loadDefaults();
    }

    private void loadDefaults() {
        config.put("memoryTable.maxSize", 64 * 1024 * 1024);
        config.put("memoryTable.maxSealedTables", 10);
        config.put("journal.batchSize", 100);
        config.put("journal.flushInterval", 1000);
        config.put("bplustree.pageSize", 4096);
        config.put("bplustree.cacheSize", 1000);
        config.put("gc.lowOccupancyThreshold", 0.3);
        config.put("gc.highOccupancyThreshold", 0.7);
        config.put("backup.dir", "backups");
        config.put("monitoring.enabled", true);
        config.put("monitoring.port", 9090);
    }

    public void loadFromFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Config file not found: " + file);
        }

        log.info("Loading configuration from file: {}", file.getAbsolutePath());

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            props.load(is);
        }

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            set(key, parseValue(value));
        }
    }

    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public void set(String key, Object value) {
        Object oldValue = config.put(key, value);
        notifyListeners(key, oldValue, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) config.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = config.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public int getInt(String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public int getInt(String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public long getLong(String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    public long getLong(String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    public double getDouble(String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    public double getDouble(String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    public boolean getBoolean(String key) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public String getString(String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    public String getString(String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public void registerListener(String key, ConfigChangeListener listener) {
        listeners.put(key, listener);
    }

    public void unregisterListener(String key) {
        listeners.remove(key);
    }

    private void notifyListeners(String key, Object oldValue, Object newValue) {
        ConfigChangeListener listener = listeners.get(key);
        if (listener != null) {
            listener.onConfigChange(key, oldValue, newValue);
        }
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(config);
    }

    public boolean contains(String key) {
        return config.containsKey(key);
    }

    public void validate() {
        validatePositive("memoryTable.maxSize", getInt("memoryTable.maxSize"));
        validatePositive("memoryTable.maxSealedTables", getInt("memoryTable.maxSealedTables"));
        validatePositive("journal.batchSize", getInt("journal.batchSize"));
        validatePositive("bplustree.pageSize", getInt("bplustree.pageSize"));
        validateThreshold("gc.lowOccupancyThreshold", getDouble("gc.lowOccupancyThreshold"));
        validateThreshold("gc.highOccupancyThreshold", getDouble("gc.highOccupancyThreshold"));
    }

    private void validatePositive(String key, int value) {
        if (value <= 0) {
            throw new KVStoreRuntimeException(ErrorCode.INVALID_ARGUMENT, 
                key + " must be positive, got: " + value);
        }
    }

    private void validateThreshold(String key, double value) {
        if (value < 0 || value > 1) {
            throw new KVStoreRuntimeException(ErrorCode.INVALID_ARGUMENT, 
                key + " must be between 0 and 1, got: " + value);
        }
    }
}
