package de.thwildau.f4f.studycompanion.datamodel;

public class SensorLoggingConfigData {
    private String source;
    private Integer interval;
    private boolean enabled;

    public SensorLoggingConfigData(String source, Integer interval, boolean enabled) {
        this.source = source;
        this.interval = interval;
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public Integer getInterval() {
        return interval;
    }

    public boolean hasInterval() {
        return interval != null;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
