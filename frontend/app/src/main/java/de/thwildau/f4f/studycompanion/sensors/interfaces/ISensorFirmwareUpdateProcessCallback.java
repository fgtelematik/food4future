package de.thwildau.f4f.studycompanion.sensors.interfaces;

public interface ISensorFirmwareUpdateProcessCallback {
    void onFirmwareUpdateAvailable(); // Firmware installation will be initiated/queued when returning true
    void onFirmwareUpdateNotAvailable();
    void onFirmwareUpdateQueued();
    void onError(Throwable e);
}
