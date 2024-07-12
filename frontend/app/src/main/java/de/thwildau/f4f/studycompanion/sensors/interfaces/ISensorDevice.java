package de.thwildau.f4f.studycompanion.sensors.interfaces;

public interface ISensorDevice {
    /**
     * Get a human-readable name of this device.
     * @return a human-readable name of this device.
     */
    String getName();
    String getMacAddress();

    /**
     *
     * @return the Pin the user needs to input for pairing. NULL if no pin required
     *
     */
    String getPairingPin();
}
