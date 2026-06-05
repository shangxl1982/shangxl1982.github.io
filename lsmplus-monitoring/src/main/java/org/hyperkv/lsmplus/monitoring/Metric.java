package org.hyperkv.lsmplus.monitoring;

public abstract class Metric {

    protected final String name;
    protected final String description;

    protected Metric(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public abstract void reset();

    public abstract String toPrometheusFormat();
}
