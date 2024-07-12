package de.thwildau.f4f.studycompanion.sensors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.Date;

import androidx.annotation.NonNull;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorFirmwareUpdateProcessCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorPairingCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorScanCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorSynchronizationState;

public abstract class SensorManagerBase {

    private static final String LOG_TAG="SensorManagerBase";
    private static final long BLE_TOO_MANY_SCAN_REQUESTS_DELAY_MS = 10000;

    private final Utils.ObservableValue<SensorConnectionState> connectionState;
    private final Utils.ObservableValue<SensorSynchronizationState> syncState ;
    private final Utils.ObservableValue<Integer> batteryLevel;
    private final Utils.ObservableValue<ISensorDevice> currentDevice;

    private static Utils.ObservableValue<Boolean> bluetoothEnabled = null;

    protected static Context getContext() {
        return StudyCompanion.getAppContext();
    }

    public SensorManagerBase() {
        connectionState = new Utils.ObservableValue<>(SensorConnectionState.DISCONNECTED);
        syncState = new Utils.ObservableValue<>(new SensorSynchronizationState());
        batteryLevel = new Utils.ObservableValue<>(null);
        currentDevice = new Utils.ObservableValue<>(null);

        registerBluetoothStateReceiver();

        syncState.addObserver((object, syncState) -> {
            if(syncState.isSynchronizationActive()) {
                Integer progress = syncState.getSynchronizationProgress();
                if(progress == null) {
                    NotificationOrganizer.showSyncNotification(NotificationOrganizer.SyncType.SensorSync);
                } else {
                    NotificationOrganizer.showSyncNotificationWithProgress(NotificationOrganizer.SyncType.SensorSync, progress);
                }
            } else {
                NotificationOrganizer.hideSyncNotification(NotificationOrganizer.SyncType.SensorSync);
            }
        });
    }

    /**
     * Method to integrate Sensor Manager in Application Life Cycle.
     *
     * Must be called when main activity's onCreate() method was invoked.
     */
    public void init() { }

    /**
     * Method to integrate Sensor Manager in Application Life Cycle.
     *
     * Must be called when main activity's onStart() method was invoked.
     */
    public void start() { }

    /**
     * Method to integrate Sensor Manager in Application Life Cycle.
     *
     * Must be called when main activity's onStop() method was invoked.
     */
    public void stop() { }

    public void notifyBluetoothConnectionStateChange(BluetoothDevice btDevice, SensorConnectionState connectionState) { }

    protected abstract void startScanningForDevicesImplementation(@NonNull ISensorScanCallback callback);
    protected abstract void stopScanningForDevicesImplementation();


    public abstract void unpairDevice();
    public abstract ISensorDevice getCurrentSensorDevice();

    public abstract void pairWithDevice(ISensorDevice device, ISensorPairingCallback callback);
    public abstract void cancelPairing();
    public abstract void startSynchronization();

    public abstract void updateConfig();

    public abstract void checkForFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback);
    public abstract void installFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback);


    /**
     * Ask custom SensorManager to asynchronously read the current battery level from device and invoke
     * invoking updateBatteryLevel(int).
     *
     * UI should call this method in regular period of time as long as battery state is visible to user.
     * Also the App / Service should invoke in regular time period to be able to notify the user,
     * when sensor needs to be recharged.
     *
     * App/UI will be notified about new battery level via Observer gathered by getObservableBatteryLevel().
     *
     */
    public abstract void acquireCurrentBatteryLevel();

    private static void registerBluetoothStateReceiver() {
        if(bluetoothEnabled != null) {
            return; // Already registered in this App session
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        bluetoothEnabled = new Utils.ObservableValue<>(bluetoothAdapter.isEnabled());

        BroadcastReceiver bluetoothEnabledStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    return;
                }

                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                if(state == BluetoothAdapter.STATE_ON ) {
                    bluetoothEnabled.setValue(true);
                } else if(state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    if(bluetoothEnabled.getValue()) { // only set to false if it was true before
                        bluetoothEnabled.setValue(false);
                    }
                }
            }
        };

        getContext().registerReceiver(bluetoothEnabledStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    public final SensorConnectionState getConnectionState() {
        return connectionState.getValue();
    }

    protected final void setConnectionState(SensorConnectionState connectionState) {
        this.connectionState.setValue(connectionState);
        if(connectionState == SensorConnectionState.DISCONNECTED) {
            if(getObservableSynchronizationState().getValue().isSynchronizationActive()) {
                setSynchronizationFailed(getContext().getString(R.string.sensor_synchronization_failed_reason_lost_connection));
            }
        }
    }

    protected void setLastSyncTime(Date lastSyncTime) {
        getObservableSynchronizationState().getValue().setLastSyncTime(lastSyncTime);
        getObservableSynchronizationState().invalidateValue();
    }

    protected void setSynchronizationStarted() {
        getObservableSynchronizationState().getValue().setSynchronizationProgress(0);
        getObservableSynchronizationState().getValue().setSynchronizationActive(true);
        getObservableSynchronizationState().invalidateValue();
    }

    protected void setSynchronizationFailed(String error) {
        getObservableSynchronizationState().getValue().setSynchronizationProgress(null);
        getObservableSynchronizationState().getValue().setSynchronizationActive(false);
        getObservableSynchronizationState().invalidateValue();

        Toast.makeText(getContext(), getContext().getString(R.string.sensor_synchronization_failed, error), Toast.LENGTH_LONG).show();
        Log.e(LOG_TAG, "Sensor synchronization failed! Error message: " + error);
    }

    protected void setSynchronizationFinished() {
        getObservableSynchronizationState().getValue().setSynchronizationProgress(null);
        getObservableSynchronizationState().getValue().setSynchronizationActive(false);
        getObservableSynchronizationState().getValue().setLastSyncTime(new Date());
        getObservableSynchronizationState().invalidateValue();
    }

    protected void setSyncProgress(int progress) {
        getObservableSynchronizationState().getValue().setSynchronizationActive(true);
        getObservableSynchronizationState().getValue().setSynchronizationProgress(progress);
        getObservableSynchronizationState().invalidateValue();
    }

    protected void updateBatteryLevel(int batteryLevel) {
        getObservableBatteryLevel().setValue(batteryLevel);
    }

    protected void setCurrentDevice(ISensorDevice currentDevice) {
        this.currentDevice.setValue(currentDevice);
    }


    public final Utils.ObservableValue<SensorConnectionState> getObservableConnectionState() {
        return connectionState;
    }

    public final Utils.ObservableValue<SensorSynchronizationState> getObservableSynchronizationState() {
        return syncState;
    }

    public final Utils.ObservableValue<Integer> getObservableBatteryLevel() {
        return batteryLevel;
    }

    public final Utils.ObservableValue<ISensorDevice> getObservableCurrentDevice() {
        return currentDevice;
    }

    public final Utils.ObservableValue<Boolean> getObservableBluetoothEnabled() {
        return bluetoothEnabled;
    }

    // the following implementation is required to protect against BLE scanner fail on  too many
    // BLE scan requests, see GitLab issue food4future#166 for details.

    private RepeatScanRunnable currentRepeatScanTask;
    private Handler repeatScanHandler = null;

    private class RepeatScanRunnable implements Runnable {
        private ISensorScanCallback scanCallback;
        private boolean cancelled;

        @Override
        public void run() {
            if (!cancelled) {
                Log.d(LOG_TAG, "No device scanned within BLE scan request delay. Restarting BLE scanner.");
                stopScanningForDevicesImplementation();
                startScanningForDevicesImplementation(scanCallback);
                runDelayed(scanCallback); // restart
            }
        }

        public void runDelayed(ISensorScanCallback scanCallback) {
            this.scanCallback = scanCallback;
            this.cancelled = false;
            repeatScanHandler.postDelayed(this, BLE_TOO_MANY_SCAN_REQUESTS_DELAY_MS);
        }

        public void cancel() {
            cancelled = true;
            repeatScanHandler.removeCallbacks(this);
        }
    }


    public final void startScanningForDevices(@NonNull ISensorScanCallback callback) {
        if(repeatScanHandler == null) {
            repeatScanHandler = new Handler(Looper.getMainLooper());
        }

        if(currentRepeatScanTask == null || currentRepeatScanTask.cancelled) {
            currentRepeatScanTask = new RepeatScanRunnable();
        }

        ISensorScanCallback scanCallback = new ISensorScanCallback() {
            @Override
            public void onScannedDevice(ISensorDevice device) {
                currentRepeatScanTask.cancel();
                callback.onScannedDevice(device);
            }

            @Override
            public void onScanFailed(String errorMsg) {
                currentRepeatScanTask.cancel();
                callback.onScanFailed(errorMsg);
            }
        };

        startScanningForDevicesImplementation(scanCallback);

        currentRepeatScanTask.runDelayed(scanCallback);
    }

    public final void stopScanningForDevices() {
        currentRepeatScanTask.cancel();
        stopScanningForDevicesImplementation();
    }


}
