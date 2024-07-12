//package de.thwildau.f4f.studycompanion.sensors.garmin;
//
//import androidx.annotation.NonNull;
//
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
//
//public class GarminSensorDevice implements ISensorDevice {
//
//    private String macAddress;
//    private String deviceName;
//
//    public GarminSensorDevice(String deviceName, String macAddress) {
//        this.macAddress = macAddress;
//        this.deviceName = deviceName;
//    }
//
//    @Override
//    public String getName() {
//        return deviceName;
//    }
//
//    @NonNull
//    @Override
//    public String toString() {
//        if (deviceName != null) {
//            return getName();
//        } else {
//            return "--";
//        }
//    }
//
//    @Override
//    public String getMacAddress() {
//        return macAddress;
//    }
//
//    @Override
//    public String getPairingPin() {
//        return null;
//    }
//
//}
