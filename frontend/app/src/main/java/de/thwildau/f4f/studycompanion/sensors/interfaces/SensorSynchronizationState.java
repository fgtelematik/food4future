package de.thwildau.f4f.studycompanion.sensors.interfaces;

import java.util.Date;

public class SensorSynchronizationState {
    private Date lastSyncTime = null;
    private boolean synchronizationActive = false;
    private Integer synchronizationProgress = null;

    public Date getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(Date lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public boolean isSynchronizationActive() {
        return synchronizationActive;
    }

    public void setSynchronizationActive(boolean synchronizationActive) {
        this.synchronizationActive = synchronizationActive;
        this.synchronizationProgress = null;
    }

    public Integer getSynchronizationProgress() {
        return synchronizationProgress;
    }

    public void setSynchronizationProgress(Integer synchronizationProgress) {
        this.synchronizationProgress = synchronizationProgress;
        if(synchronizationProgress != null && synchronizationProgress < 100 && synchronizationProgress > 0) {
            this.synchronizationActive = true;
        }
    }
}
