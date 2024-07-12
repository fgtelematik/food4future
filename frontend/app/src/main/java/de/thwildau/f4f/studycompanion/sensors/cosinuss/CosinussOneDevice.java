package de.thwildau.f4f.studycompanion.sensors.cosinuss;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;

public class CosinussOneDevice implements ISensorDevice {

    private String macAddress;
    private String deviceName;

    public CosinussOneDevice(String deviceName, String macAddress) {
        this.macAddress = macAddress;
        this.deviceName = deviceName;
    }

    @Override
    public String getName() {
        return deviceName;
    }

    @NonNull
    @Override
    public String toString() {
        if (deviceName != null) {
            return getName();
        } else {
            return "--";
        }
    }

    @Override
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * @return The pin user needs to enter during pairing, null if no pin is required
     */
    @Override
    public String getPairingPin() {
        if("one".equals(deviceName)) {
            // requiring this pin was introduced with new firmware (v2) of cosinuss one.
            return "111111";
        }
        return null;
    }

    public boolean equals(ISensorDevice sensorDevice) {
        return sensorDevice.getMacAddress().equals(macAddress);
    }

    public boolean equals(BluetoothDevice sensorDevice) {
        return sensorDevice.getAddress().equals(macAddress);
    }



}
