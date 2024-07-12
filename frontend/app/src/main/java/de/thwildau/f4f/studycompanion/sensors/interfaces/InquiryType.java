package de.thwildau.f4f.studycompanion.sensors.interfaces;

public enum InquiryType {

    /**
     * Request to reset device.
     *
     * Must return boolean in InquiryResponse (true - do reset, false - no reset)
     */
    REQUEST_RESET_DEVICE,

    /**
     * Request a bluetooth authentication code.
     *
     * Must return the auth code in InquiryResponse as Integer
     */
    REQUEST_AUTH_CODE,

    /**
     * A former reset request was cancelled and can not be answered anymore.
     */
    RESET_REQUEST_CANCELLED
}
