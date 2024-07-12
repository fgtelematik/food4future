package de.thwildau.f4f.studycompanion.ui.sensors;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.sensors.SensorManagerBase;
//import de.thwildau.f4f.studycompanion.sensors.garmin.GarminSensorManager;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorPairingCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorScanCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.InquiryResponse;
import de.thwildau.f4f.studycompanion.sensors.interfaces.InquiryType;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorSynchronizationState;

public class SensorManagementViewModel extends ViewModel {

    private MutableLiveData<List<ISensorDevice>> mScannedDevices;
    private MutableLiveData<ISensorDevice> mPairedDevice;
    private MutableLiveData<SensorConnectionState> mConnectionState;
    private MutableLiveData<String> mToastMessage;
    private MutableLiveData<SensorSynchronizationState> mSyncState;
    private MutableLiveData<Integer> mBatteryLevel;
    private MutableLiveData<Boolean> mScanning;
    private MutableLiveData<Boolean> mPairing;
    private MutableLiveData<Boolean> mAuthCodeRequested;
    private MutableLiveData<Boolean> mResetRequested;
    private MutableLiveData<Boolean> mResetRequestCancelled;
    private MutableLiveData<Boolean> mBluetoothEnabled;
    private MutableLiveData<Boolean> mGarminSdkInitializationError;

    private SensorManagerBase mSensorManager;

    private InquiryResponse resetRequestReponse = null;
    private InquiryResponse authRequestReponse = null;

    private Utils.Observer<Integer> mBatteryLevelObserver;
    private Utils.Observer<SensorSynchronizationState> mSynchronizationStateObserver;
    private Utils.Observer<SensorConnectionState> mConnectionStateObserver;
    private Utils.Observer<ISensorDevice> mPairedDeviceObserver;
    private Utils.Observer<Boolean> mBluetoothEnabledObserver;
    private Utils.Observer<Boolean> mGarminSdkInitializationErrorObserver;

    private boolean scanningRequested = true;

    public SensorManagementViewModel() {

        mScannedDevices = new MutableLiveData<>();
        mPairedDevice = new MutableLiveData<>();
        mConnectionState = new MutableLiveData<>();
        mToastMessage = new MutableLiveData<>(null);
        mSyncState = new MutableLiveData<>();
        mBatteryLevel = new MutableLiveData<>();
        mScanning = new MutableLiveData<>();
        mPairing = new MutableLiveData<>();
        mAuthCodeRequested = new MutableLiveData<>();
        mResetRequested = new MutableLiveData<>();
        mResetRequestCancelled = new MutableLiveData<>();
        mBluetoothEnabled = new MutableLiveData<>();
        mGarminSdkInitializationError = new MutableLiveData<>(false);
    }

    public void init(SensorManagerBase sensorManager) {
        mSensorManager = sensorManager;
        mScannedDevices.setValue(new LinkedList<>());
        mScanning.setValue(false);
        mPairing.setValue(false);
        mBluetoothEnabled.setValue(mSensorManager.getObservableBluetoothEnabled().getValue());

        mBatteryLevelObserver = mSensorManager.getObservableBatteryLevel().addObserver((object, data) -> mBatteryLevel.postValue(data), true);
        mSynchronizationStateObserver = mSensorManager.getObservableSynchronizationState().addObserver((object, data) -> mSyncState.postValue(data), true);
        mConnectionStateObserver = mSensorManager.getObservableConnectionState().addObserver((object, data) -> mConnectionState.postValue(data), true);
        mPairedDeviceObserver = mSensorManager.getObservableCurrentDevice().addObserver((object, data) -> mPairedDevice.postValue(data), true);

//        if(mSensorManager instanceof GarminSensorManager) {
//            GarminSensorManager garminSensorManager = (GarminSensorManager)sensorManager;
//            mGarminSdkInitializationErrorObserver = garminSensorManager.getObservableSdkInitializationError().addObserver((object, data) -> mGarminSdkInitializationError.postValue(data), true);
//        } else {
            mGarminSdkInitializationError.postValue(false);
//        }

        mBluetoothEnabledObserver = mSensorManager.getObservableBluetoothEnabled().addObserver((object, btEnabled) -> {
            mBluetoothEnabled.postValue(btEnabled);
            if(btEnabled && scanningRequested) {
                scanningRequested = false;
                tryStartScanning();
            }
        });
    }

    public void release() {
        mSensorManager.getObservableBatteryLevel().removeObserver(mBatteryLevelObserver);
        mSensorManager.getObservableSynchronizationState().removeObserver(mSynchronizationStateObserver);
        mSensorManager.getObservableConnectionState().removeObserver(mConnectionStateObserver);
        mSensorManager.getObservableCurrentDevice().removeObserver(mPairedDeviceObserver);
    }

    public void startSync() {
        mSensorManager.startSynchronization();
    }

    public void unpairDevice() {
        mSensorManager.unpairDevice();
    }

    private void invalidateScannedDevicesList() {
        mScannedDevices.postValue(mScannedDevices.getValue());
    }

    public void startScanning() {
        if(mSensorManager.getObservableBluetoothEnabled().getValue()) {
            tryStartScanning();
        } else {
            scanningRequested = true;
        }
    }

    private void tryStartScanning() {
        List<ISensorDevice> scannedDevices = mScannedDevices.getValue();
        scannedDevices.clear();
        mScannedDevices.setValue(scannedDevices);
        mScannedDevices.setValue(scannedDevices);
        mScanning.setValue(true);

        mSensorManager.startScanningForDevices(new ISensorScanCallback() {
            @Override
            public void onScannedDevice(ISensorDevice device) {
                scannedDevices.add(device);
                invalidateScannedDevicesList();
            }

            @Override
            public void onScanFailed(String errorMsg) {
                scanningRequested = true; // continue scanning when bluetooth is turned on
                mToastMessage.postValue(errorMsg);
                mScanning.postValue(false);
            }
        });
    }

    public void stopScanning() {
        scanningRequested = false;
        mSensorManager.stopScanningForDevices();
        mScanning.postValue(false);
    }

    public void cancelPairing() {
        mSensorManager.cancelPairing();
    }

    public void pairWithDevice(ISensorDevice device) {
        mPairing.setValue(true);
        mSensorManager.pairWithDevice(device, new ISensorPairingCallback() {
            @Override
            public void onPairingSucceeded() {
                mPairing.postValue(false);
                mToastMessage.postValue(StudyCompanion.getAppContext().getString(R.string.sensor_pairing_successful, device.getName()));
            }

            @Override
            public void onPairingFailed(String errorMsg) {
                mPairing.postValue(false);
                mToastMessage.postValue(errorMsg);
            }

            @Override
            public void onPairingCancelled() {
                mPairing.postValue(false);
            }

            @Override
            public void onInquiry(InquiryType inquiryType, InquiryResponse response) {
                switch (inquiryType) {
                    case REQUEST_AUTH_CODE:
                        authRequestReponse = response;
                        mAuthCodeRequested.postValue(true);
                        break;

                    case REQUEST_RESET_DEVICE:
                        resetRequestReponse = response;
                        mResetRequested.postValue(true);
                        break;

                    case RESET_REQUEST_CANCELLED:
                        mResetRequestCancelled.postValue(true);
                }
            }
        });
    }

    public void setAuthCode(int authCode) {
        if (authRequestReponse == null) {
            return;
        }

        authRequestReponse.setRepsonse(authCode);
        mAuthCodeRequested.postValue(false);
    }

    public void answerResetRequest(boolean doReset) {
        if (resetRequestReponse == null) {
            return;
        }

        resetRequestReponse.setRepsonse(doReset);
        mResetRequested.postValue(false);
    }

    public MutableLiveData<List<ISensorDevice>> getScannedDevices() {
        return mScannedDevices;
    }

    public MutableLiveData<ISensorDevice> getPairedDevice() {
        return mPairedDevice;
    }

    public MutableLiveData<SensorConnectionState> getConnectionState() {
        return mConnectionState;
    }

    public MutableLiveData<String> getToastMessage() {
        return mToastMessage;
    }

    public MutableLiveData<SensorSynchronizationState> getSyncState() {
        return mSyncState;
    }

    public MutableLiveData<Integer> getBatteryLevel() {
        return mBatteryLevel;
    }

    public MutableLiveData<Boolean>  getScanning() {
        return mScanning;
    }

    public MutableLiveData<Boolean> getPairing() {
        return mPairing;
    }

    public MutableLiveData<Boolean> getAuthCodeRequested() {
        return mAuthCodeRequested;
    }

    public MutableLiveData<Boolean> getResetRequested() {
        return mResetRequested;
    }

    public MutableLiveData<Boolean> getResetRequestCancelled() {
        return mResetRequestCancelled;
    }

    public MutableLiveData<Boolean> getBluetoothEnabled() {
        return mBluetoothEnabled;
    }

    public MutableLiveData<Boolean> getGarminSdkInitializationError() {
        return mGarminSdkInitializationError;
    }
}