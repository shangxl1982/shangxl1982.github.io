package org.hyperkv.lsmplus.config;

public interface ConfigChangeListener {
    void onConfigChange(String key, Object oldValue, Object newValue);
}
