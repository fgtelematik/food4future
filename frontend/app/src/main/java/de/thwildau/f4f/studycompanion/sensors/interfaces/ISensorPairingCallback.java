package de.thwildau.f4f.studycompanion.sensors.interfaces;

public interface ISensorPairingCallback {
    void onPairingSucceeded();
    void onPairingFailed(String errorMsg);
    void onPairingCancelled();
    void onInquiry(InquiryType inquiryType, InquiryResponse response);
}
