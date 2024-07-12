package de.thwildau.f4f.studycompanion.sensors.interfaces;

public interface ISensorScanCallback {
    void onScannedDevice(ISensorDevice device);
    void onScanFailed(String errorMsg);
}
