//package de.thwildau.f4f.studycompanion.sensors.garmin;
//
//import android.content.SharedPreferences;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//
//import com.garmin.health.AbstractGarminHealth;
//import com.garmin.health.AuthCompletion;
//import com.garmin.health.ConnectionState;
//import com.garmin.health.Device;
//import com.garmin.health.DeviceConnectionStateListener;
//import com.garmin.health.DeviceManager;
//import com.garmin.health.FirmwareResultListener;
//import com.garmin.health.GarminDeviceScanner;
//import com.garmin.health.GarminHealth;
//import com.garmin.health.GarminRequestManager;
//import com.garmin.health.HealthSDKLogging;
//import com.garmin.health.LoggingLevel;
//import com.garmin.health.PairingCallback;
//import com.garmin.health.ResetCompletion;
//import com.garmin.health.ScannedDevice;
//import com.garmin.health.bluetooth.FailureCode;
//import com.garmin.health.bluetooth.PairingFailedException;
//import com.garmin.health.customlog.LoggingSyncListener;
//import com.garmin.health.firmware.FirmwareDownload;
//import com.garmin.health.settings.DeviceSettings;
//import com.garmin.health.settings.Settings;
//import com.garmin.health.settings.SupportStatus;
//import com.garmin.health.settings.UnitSettings;
//import com.garmin.health.settings.UserSettings;
//import com.garmin.health.sync.SyncException;
//import com.garmin.health.sync.SyncListener;
//import com.google.common.util.concurrent.FutureCallback;
//import com.google.common.util.concurrent.Futures;
//import com.google.common.util.concurrent.SettableFuture;
//
//import org.json.JSONObject;
//
//import java.io.File;
//import java.io.InputStream;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.Callable;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//
//import de.thwildau.f4f.studycompanion.R;
//import de.thwildau.f4f.studycompanion.StudyCompanion;
//import de.thwildau.f4f.studycompanion.Utils;
//import de.thwildau.f4f.studycompanion.backend.BackendIO;
//import de.thwildau.f4f.studycompanion.datamodel.DataManager;
//import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
//import de.thwildau.f4f.studycompanion.datamodel.User;
//import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
//import de.thwildau.f4f.studycompanion.sensors.SensorManagerBase;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorFirmwareUpdateProcessCallback;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorPairingCallback;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorScanCallback;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.InquiryType;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;
//import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorSynchronizationState;
//
//public class GarminSensorManager extends SensorManagerBase implements DeviceConnectionStateListener {
//    private static final String LOG_TAG = "GarminSensorManager";
//    private static final int MAX_START_ATTEMPTS = 10;
//
//    private final Utils.ObservableValue<Boolean> sdkError = new Utils.ObservableValue<>(false);
//
//    private GarminDeviceScanner deviceScanner = null;
//    private final Map<ISensorDevice, ScannedDevice> scannedDevices = new HashMap<>();
//    private boolean acquiringBatteryLevel = false;
//    private final GarminSyncListener syncListener = new GarminSyncListener(SyncMode.SYNC_DATA);
//    private final GarminSyncListener loggingSyncListener = new GarminSyncListener(SyncMode.LOGGING_DATA);
//    private boolean loggingSyncActive = false;
//    private boolean isConfigUpdating = false;
//    private boolean configUpdateRequestedAfterSyncFinished = false;
//    private boolean isStarted = false;
//
//    private int numStartAttempts = 0;
//
//    private Future pairingFuture = null;
//    private ISensorPairingCallback sensorPairingCallback;
//    private boolean resetDone = false;
//    private Date lastTimeSyncStored = null;
//
//    private SettableFuture<List<FirmwareDownload>> firmwareRequest;
//    private String firmwareRequestDeviceAddr;
//
//    private SharedPreferences preferences;
//
//
//    private enum SyncMode {
//        SYNC_DATA,
//        LOGGING_DATA
//    }
//
//    private class PairingObserver implements PairingCallback {
//        @Override
//        public void pairingSucceeded(Device device) {
//            if (sensorPairingCallback != null) {
//                sensorPairingCallback.onPairingSucceeded();
//                pairingFuture = null;
//                sensorPairingCallback = null;
//                BackendIO.serverLog(Log.INFO, LOG_TAG, "Paired new Garmin Device with MAC Address: " + device.address());
//                updateCurrentDevice();
//            }
//
//            // completely stop scanning, since we are now paired
//            deviceScanner = null;
//        }
//
//        @Override
//        public void pairingFailed(PairingFailedException e) {
//            if (sensorPairingCallback != null) {
//                String errorMsg = e.getLocalizedMessage();
//                if(errorMsg == null) {
//                    errorMsg = e.toString();
//                } else {
//                    errorMsg = errorMsg + " (" + e + ")";
//                }
//
//                BackendIO.serverLog(Log.ERROR, LOG_TAG, "Pairing failed. Error: " + errorMsg);
//                sensorPairingCallback.onPairingFailed(getContext().getString(R.string.sensor_pairing_failed, errorMsg));
//                pairingFuture = null;
//                sensorPairingCallback = null;
//            }
//            if(deviceScanner != null) {
//                // restart scanning
//                DeviceManager.getDeviceManager().registerGarminDeviceScanner(deviceScanner);
//            }
//        }
//
//        @Override
//        public void authRequested(final AuthCompletion authCompletion) {
//            // implementation taken from Garmin Sample App
//            if (pairingFuture.isCancelled()) {
//                return;
//            }
//
//            if (sensorPairingCallback != null) {
//                sensorPairingCallback.onInquiry(InquiryType.REQUEST_AUTH_CODE, response -> {
//                    int passkey = (int) response;
//                    authCompletion.setPasskey(passkey);
//                });
//            }
//        }
//
//
//        @Override
//        public void resetRequested(@NonNull ResetCompletion completion) {
//            if (pairingFuture.isCancelled()) {
//                return;
//            }
//
//            new Handler(Looper.getMainLooper()).post(() ->
//            {
//                if (resetDone) {
//                    completion.shouldReset(false);
//                } else {
//                    if (sensorPairingCallback != null) {
//                        sensorPairingCallback.onInquiry(InquiryType.REQUEST_RESET_DEVICE, response -> {
//                            boolean shouldReset = (boolean) response;
//                            if (shouldReset) {
//                                resetDone = true;
//                            }
//                            completion.shouldReset(shouldReset);
//                        });
//                    }
//                }
//            });
//        }
//
//        @Override
//        public void authTimeout() {
//            PairingCallback.super.authTimeout();
//        }
//
//        @Override
//        public void resetRequestCancelled() {
//            if (sensorPairingCallback != null) {
//                sensorPairingCallback.onInquiry(InquiryType.RESET_REQUEST_CANCELLED, null);
//            }
//        }
//    }
//
//    public GarminSensorManager() {
//        super();
//    }
//
//    public Utils.ObservableValue<Boolean> getObservableSdkInitializationError() {
//        return sdkError;
//    }
//
//    @Override
//    public void init() {
//        preferences = StudyCompanion.getGlobalPreferences();
//
//        try {
//
//            if (!GarminHealth.isInitialized() || sdkError.getValue()) {
//
//                String garminLicenseKey = SchemaProvider.getDeviceConfig().getGarminLicenseKey();
//                if(Utils.nullOrEmpty(garminLicenseKey)) {
//                    if(sdkError.getValue()) {
//                        BackendIO.serverLog(Log.ERROR, LOG_TAG,"Could not initialize Garmin SDK since no Garmin license key was available.");
//                    }
//                    sdkError.setValue(true);
//                    return;
//                }
//
//                try {
//                    HealthSDKLogging.setLoggingLevel(LoggingLevel.NORMAL);
////                    InputStream garminLogs = HealthSDKLogging.getSDKLogStream(getContext()); //TODO: Handle logging stream
//
//                } catch(IllegalStateException e) {
//                    // ignore
//                }
//
//                Log.d(LOG_TAG, "Initializing Garmin SDK...");
//
//                numStartAttempts = 0;
//
//                // Initialize the Garmin SDK.
//                GarminHealth.initialize(getContext(), garminLicenseKey);
//
//            }
//
//            if(sdkError.getValue())
//                sdkError.setValue(false);
//
//        } catch (Exception e) {
//            sdkError.setValue(true);
//            Log.e(LOG_TAG, "Garmin SDK Initialization failed");
//            String errorMsg = e.getMessage();
//            if(errorMsg == null)
//                errorMsg = "n/a";
//
//            // Toast.makeText(getContext(), getContext().getString(R.string.error_garmin_initsdk, e.getMessage()), Toast.LENGTH_LONG).show();
//            // Temporarily disable warning for testing period without valid Garmin license available.
//            // We just server-log the error instead.
//            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Exception thrown when trying to initialize Garmin SDK. Error Message: \"" + errorMsg + "\"" );
//
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * This method must be called from within onStop() of the MainActivity.
//     */
//    @Override
//    public void stop() {
//        isStarted = false;
//        numStartAttempts = 0;
//
//        if (GarminHealth.isInitialized() && !sdkError.getValue()) {
//            // Stop the Health SDK to pause or close resource currently running in the application space.
//            DeviceManager.getDeviceManager().removeSyncListener(syncListener);
//            DeviceManager.getDeviceManager().removeLoggingSyncListener(syncListener);
//            DeviceManager.getDeviceManager().removeConnectionStateListener(this);
//            getObservableSynchronizationState().removeObserver(updateConfigAfterSyncHandler);
//
//            //GarminHealth.stop();
//            // This needs further testing... Due to Garmin Docs, calling GarminHealth.stop() is necessary when MainActivity stops, could otherwise lead to memory leaks.
//            // But if we stop the GarminHealth SDK, a currently running sync process will be interrupted and the Garmin synchronization can never processed in Background.
//            // So, we keep it running for now, since we actually want the Garmin Service to stay active in background.  We need to observe, if this really leads to problems.
//            // All references to GUI components are detached, if the MainActivity stops, so any NPE's should be avoided when having the app running in background.
//
//        }
//    }
//
//    /**
//     * This method must be called from within onStart() of the MainActivity.
//     */
//    @Override
//    public void start() {
//        if(isStarted) {
//            // Only start after stop was called.
//            return;
//        }
//
//        Log.d(LOG_TAG, "Starting GarminSensorManager...");
//
//        if(!GarminHealth.isInitialized() && !sdkError.getValue()) {
//            numStartAttempts++;
//
//            if(numStartAttempts < MAX_START_ATTEMPTS) {
//                // Try again
//                Log.d(LOG_TAG, "Garmin SDK not initialized. Will try starting again in 1 second.");
//                new Handler().postDelayed(this::start, 1000);
//            } else {
//                BackendIO.serverLog(Log.ERROR, LOG_TAG, "Failed to start GarminSensorManager. Maximum number of start attempts reached.");
//                Toast.makeText(getContext(), "Failed to start GarminSensorManager. SDK cannot be initialized." + numStartAttempts, Toast.LENGTH_SHORT).show();
//                sdkError.setValue(true);
//            }
//            return;
//        }
//
//
//        if (GarminHealth.isInitialized() && !sdkError.getValue()) {
//            // Restart the Health SDK in the current application space.
//            GarminHealth.restart(getContext());
//
//            DeviceManager deviceManager = DeviceManager.getDeviceManager();
//
//            deviceManager.addSyncListener(syncListener);
//            deviceManager.addLoggingSyncListener(loggingSyncListener);
//            deviceManager.addConnectionStateListener(this);
//
//            getObservableSynchronizationState().addObserver(updateConfigAfterSyncHandler);
//
//            String lastSyncTimePrefId = getContext().getString(R.string.lastGarminSensorSyncTime);
//            long lastSyncTimeMs = preferences.getLong(lastSyncTimePrefId, 0);
//            if (lastSyncTimeMs != 0) {
//                lastTimeSyncStored = new Date(lastSyncTimeMs);
//                setLastSyncTime(lastTimeSyncStored);
//            }
//
//            Device garminDevice = getPairedGarminDevice();
//
//            if (garminDevice != null) {
//                // Update initial device connection state
//                ConnectionState garminConnectionState = garminDevice.connectionState();
//                setConnectionState(convertConnectionState(garminConnectionState));
//            }
//
//            updateCurrentDevice();
//
//            isStarted = true;
//
//            Log.d(LOG_TAG, "GarminSensorManager successfully started.");
//        }
//    }
//
//    private SensorConnectionState convertConnectionState(ConnectionState garminConnectionState) {
//        SensorConnectionState res = null;
//        switch (garminConnectionState) {
//            case CONNECTED:
//                res = SensorConnectionState.CONNECTED;
//                break;
//            case CONNECTING:
//                res = SensorConnectionState.CONNECTING;
//                break;
//            case DISCONNECTED:
//                res = SensorConnectionState.DISCONNECTED;
//                break;
//        }
//        return res;
//    }
//
//    @Override
//    public void startScanningForDevicesImplementation(@NonNull ISensorScanCallback callback) {
//        if(!GarminHealth.isInitialized() || sdkError.getValue()) {
//            callback.onScanFailed("Garmin Health SDK not initialized.");
//            return;
//        }
//
//        if (deviceScanner != null) {
//            stopScanningForDevicesImplementation();
//        }
//        scannedDevices.clear();
//
//        deviceScanner = new GarminDeviceScanner() {
//
//            @Override
//            public void onScannedDevice(ScannedDevice scannedDevice) {
//                final String deviceName = scannedDevice.friendlyName();
//                final String deviceMacAddress = scannedDevice.address();
//
//                ISensorDevice scannedSensorDevice = null;
//
//                for (ISensorDevice alreadyScannedDevice : scannedDevices.keySet()) {
//                    // with new Garmin SDK, the same devices might be scanned multiple times
//                    // so we check, if we already scanned this device and replace it on demand
//                    if(alreadyScannedDevice.getMacAddress().equals(scannedDevice.address())) {
//                        scannedSensorDevice = alreadyScannedDevice;
//                    }
//                }
//
//                if(scannedSensorDevice == null) {
//                    // Device was scanned for the first time. So we create a new ISensorDevice and notify observer
//                    scannedSensorDevice = new GarminSensorDevice(deviceName, deviceMacAddress);
//                    scannedDevices.put(scannedSensorDevice, scannedDevice);
//                    callback.onScannedDevice(scannedSensorDevice);
//                } else {
//                    // Device was scanned before. We just update the internal device reference
//                    scannedDevices.put(scannedSensorDevice, scannedDevice);
//                }
//            }
//
//            @Override
//            public void onScanFailed(Integer errorCode) {
//                callback.onScanFailed(getContext().getString(R.string.sensor_scan_error_msg, errorCode));
//            }
//        };
//
//        DeviceManager.getDeviceManager().registerGarminDeviceScanner(deviceScanner);
//
//        Log.d(LOG_TAG, "BLE Scanning started.");
//    }
//
//    @Override
//    public void stopScanningForDevicesImplementation() {
//        if(!GarminHealth.isInitialized() || sdkError.getValue() ) {
//            return;
//        }
//
//        if (deviceScanner != null) {
//            Log.d(LOG_TAG, "BLE Scanning stopped.");
//            DeviceManager.getDeviceManager().unregisterGarminDeviceScanner(deviceScanner);
//            deviceScanner = null;
//        }
//    }
//
//    @Override
//    public void unpairDevice() {
//        Device currentDevice = getPairedGarminDevice();
//        if (currentDevice != null) {
//            BackendIO.serverLog(Log.INFO, LOG_TAG, "Unpaired Garmin Device with MAC Address: " + currentDevice.address());
//            setCurrentDevice(null);
//            setConnectionState(SensorConnectionState.DISCONNECTED);
//
//            // since Garmin SDK upgrade to 2.4.4, unpairing device on main thread cause App to crash,
//            // so we run it on a separate thread
//            new Thread(() -> DeviceManager.getDeviceManager().forget(currentDevice.address())).start();
//
//            if(getObservableSynchronizationState().getValue().isSynchronizationActive()) {
//                setSynchronizationFailed("Garmin Device unpaired.");
//                loggingSyncActive = false;
//            }
//
//        }
//
//    }
//
//    private void updateCurrentDevice() {
//        setCurrentDevice(getCurrentSensorDevice());
//    }
//
//
//    private Device getPairedGarminDevice() {
//        if(!GarminHealth.isInitialized() || sdkError.getValue() || DeviceManager.getDeviceManager() == null) {
//            return null;
//        }
//
//        Set<Device> devices = DeviceManager.getDeviceManager().getPairedDevices();
//
//        if(devices == null) { // from Garmin SDK v2.3.0 this can happen when initialized Garmin SDK was not properly initialized
//            return null;
//        }
//
//        Device garminDevice = null;
////        boolean moreThanOneDevice = false;
//        Iterator<Device> i = devices.iterator();
//
//        while (i.hasNext()) {
//            Device device = i.next();
//
//            if (garminDevice != null) {
////                moreThanOneDevice = true;
//                // forget all devices except for first
//                DeviceManager.getDeviceManager().forget(device.address());
//                return null;
//            } else {
//                garminDevice = device;
//            }
//        }
//
//        return garminDevice;
//    }
//
//
//    @Override
//    public GarminSensorDevice getCurrentSensorDevice() {
//        Device garminDevice = getPairedGarminDevice();
//
//        if (garminDevice == null) {
//            // Not paired with any device
//            return null;
//        }
//
////        if(moreThanOneDevice) {
////            Log.w(LOG_TAG, "Paired with more than one Garmin Device! Making forget all paired Garmin devices.");
////            return null;
////        }
//
//        // Paired with ONE Garmin device:
//        return new GarminSensorDevice(garminDevice.friendlyName(), garminDevice.address());
//
//    }
//
//    @Override
//    public void pairWithDevice(ISensorDevice device, ISensorPairingCallback callback) {
//
//        sensorPairingCallback = callback;
//
//        if (!(device instanceof GarminSensorDevice)) {
//            throw new IllegalArgumentException("GarminSensorManager can only pair with GarminSensorDevice.");
//        }
//
//        GarminSensorDevice sensorDevice = (GarminSensorDevice) device;
//
//        ScannedDevice scannedDevice = scannedDevices.get(sensorDevice);
//
//        if (scannedDevice == null) {
//            throw new IllegalArgumentException("Trying to pair with a device which was not scanned during last scanning process.");
//        }
//
//        if (getPairedGarminDevice() != null) {
//            // Unpair first, if already paired with other device
//            unpairDevice();
//        }
//
//        if (pairingFuture != null) {
//            // cancel ongoing pairing process
//            cancelPairing();
//        }
//
//        resetDone = false;
//
//        GarminSettingsProvider settingsProvider = new GarminSettingsProvider(scannedDevice.deviceModel(), BackendIO.getCurrentUser());
//
//        UserSettings userSettings = settingsProvider.buildUserSettings();
//        DeviceSettings deviceSettings = settingsProvider.buildDeviceSettings();
//        UnitSettings unitSettings = settingsProvider.buildUnitSettings();
//
//        if(deviceScanner != null) {
//            // interrupt scanning during pairing process
//            DeviceManager.getDeviceManager().unregisterGarminDeviceScanner(deviceScanner);
//        }
//
//        try {
//            pairingFuture = DeviceManager.getDeviceManager().pair(scannedDevice, userSettings, deviceSettings, unitSettings, new PairingObserver());
//        } catch(Throwable e) {
//            BackendIO.serverLog(Log.ERROR, "GarminSensorManager", "Pairing failed. Exception: " + e);
//        }
//    }
//
//    @Override
//    public void cancelPairing() {
//        if (pairingFuture != null) {
//            pairingFuture.cancel(true);
//            pairingFuture = null;
//
//            if (sensorPairingCallback != null) {
//                sensorPairingCallback.onPairingCancelled();
//                sensorPairingCallback = null;
//            }
//
//            if(deviceScanner != null) {
//                // restart scanning
//                DeviceManager.getDeviceManager().registerGarminDeviceScanner(deviceScanner);
//            }
//        }
//    }
//
//    @Override
//    public void startSynchronization() {
//        // Initialization of Garmin SDK might have been lost, when called from background Worker
//        // so re-init and re-start framework, if necessary
//        init();
//        start();
//
//        if(!GarminHealth.isInitialized() || sdkError.getValue()) {
//            return;
//        }
//
//        Device device = getPairedGarminDevice();
//        if (device == null) {
//            return;
//        }
//        if (getConnectionState() != SensorConnectionState.CONNECTED) {
//            return;
//        }
//
//        // Trigger Sync.
//        device.requestSync();
//    }
//
//    @Override
//    public void acquireCurrentBatteryLevel() {
//        Device device = getPairedGarminDevice();
//        if(device == null) {
//            return;
//        }
//
//        if (acquiringBatteryLevel) {
//            // Already trying to acquire battery level
//            return;
//        }
//
//        acquiringBatteryLevel = true;
//
//        if (device.batteryPercentageSupportStatus() == SupportStatus.ENABLED) {
//
//            Futures.addCallback(device.batteryPercentage(), new FutureCallback<Integer>() {
//                @Override
//                public void onSuccess(Integer result) {
//                    acquiringBatteryLevel = false;
//                    updateBatteryLevel(result);
//                }
//
//                @Override
//                public void onFailure(Throwable t) {
//                    // Try again after 5 seconds on error
//                    Executors.newSingleThreadScheduledExecutor().schedule(() ->
//                            acquireCurrentBatteryLevel(), 5, TimeUnit.SECONDS);
//                    acquiringBatteryLevel = false;
//                }
//            }, Executors.newSingleThreadExecutor());
//        } else if (device.batteryPercentageSupportStatus() == SupportStatus.DISCONNECTED_UNKNOWN) {
//            // Try again after 5 seconds on unknown state
//            Executors.newSingleThreadScheduledExecutor().schedule(this::acquireCurrentBatteryLevel, 5, TimeUnit.SECONDS);
//            acquiringBatteryLevel = false;
//        } else {
//            // Battery level acquisition not supported
//            acquiringBatteryLevel = false;
//        }
//    }
//
//    @Override
//    public void updateConfig() {
//        if(!GarminHealth.isInitialized() || sdkError.getValue()) {
//            // Reason for a formerly failed initialization might be a bad license key.
//            // This license key might have been updated after config change, so try to initialize again:
//            init();
//            start();
//        }
//
//        if(!GarminHealth.isInitialized() || sdkError.getValue()) {
//            // If initialization still fails, give up
//            return;
//        }
//
//        if(getObservableSynchronizationState().getValue().isSynchronizationActive()) {
//            configUpdateRequestedAfterSyncFinished = true;
//            return;
//        }
//        if(isConfigUpdating) {
//            return;
//        }
//
//        isConfigUpdating = true;
//        new Thread(() -> {
//            try {
//                if (getConnectionState() == SensorConnectionState.CONNECTED) {
//
//                    boolean error = false;
//
//                    Device device = getPairedGarminDevice();
//                    User currentUser = BackendIO.getCurrentUser();
//                    if (currentUser != null && currentUser.role == Role.Participant && device != null) {
//                        GarminSettingsProvider settingsProvider = new GarminSettingsProvider(device.model(), currentUser);
//                        try {
//                            Settings settings = device.settings().get();
//
//                            Log.d(LOG_TAG, "Updating Garmin Device Settings...");
//                            settings.updateDeviceSettings(settingsProvider.buildDeviceSettings());
//                            Log.d(LOG_TAG, "Successfully updated Garmin Device Settings.");
//
//                            Log.d(LOG_TAG, "Updating Garmin Unit Settings...");
//                            settings.updateUnitSettings(settingsProvider.buildUnitSettings());
//                            Log.d(LOG_TAG, "Successfully updated Garmin Unit Settings.");
//
//                            Log.d(LOG_TAG, "Updating Garmin User Settings...");
//                            settings.updateUserSettings(settingsProvider.buildUserSettings());
//                            Log.d(LOG_TAG, "Successfully updated Garmin User Settings.");
//
//                            device.updateSettings(settings);
//
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                            error = true;
//                        }
//                    }
//
//                    if (!error) {
//                        error = !GarminSensorConfigurator.configureLogging(device);
//                    }
//
//                    // Errors in update process might be caused by a running
//                    // synchronization process, which was started in parallel
//                    if (error && getObservableSynchronizationState().getValue().isSynchronizationActive()) {
//                        // in this case, we trigger a re-call of updateConfig() after sync is done.
//                        configUpdateRequestedAfterSyncFinished = true;
//                        Log.d(LOG_TAG, "Garmin Sensor Config Update interrupted by error. Will retry after current synchronization finished.");
//                    } else if (error) {
//                        Log.e(LOG_TAG, "Garmin Sensor Config Update interrupted by error, even though no active sync. Will retry after next synchronization finished.");
//                        configUpdateRequestedAfterSyncFinished = true;
//                    }
//                }
//            } catch(Exception e) {
//                BackendIO.serverLog(Log.ERROR, LOG_TAG,"ERROR on applying Sensor configuration: " + e);
//            } finally {
//                isConfigUpdating = false;
//            }
//        }).start();
//    }
//
//    @Override
//    public void onDeviceConnected(@NonNull Device device) {
//        setConnectionState(SensorConnectionState.CONNECTED);
//        acquireCurrentBatteryLevel();
//        updateConfig();
//    }
//
//    @Override
//    public void onDeviceDisconnected(@NonNull Device device) {
//        setConnectionState(SensorConnectionState.DISCONNECTED);
//    }
//
//    @Override
//    public void onDeviceConnectionFailed(@NonNull Device device, @NonNull FailureCode failureCode) {
//        Log.w(LOG_TAG, "Connection with Garmin device failed. Reason: " + failureCode.name());
//        setConnectionState(SensorConnectionState.DISCONNECTED);
//    }
//
//    @Override
//    public void checkForFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback) {
//        IllegalStateException noConnectedDeviceException = new IllegalStateException("No connected Garmin sensor device.");
//        Device device = getPairedGarminDevice();
//        if (device == null || device.connectionState() != ConnectionState.CONNECTED) {
//            callback.onError(noConnectedDeviceException);
//            return;
//        }
//
//        new Thread(() ->
//        {
//            try
//            {
//                final String dirPath = getContext().getFilesDir().toString() + "/" + device.address().hashCode();
//                final File dir = new File(dirPath);
//                dir.mkdirs();
//
//                Log.i(LOG_TAG, "Checking for available firmware update for Garmin device: " + device.friendlyName() + "/" + device.address());
//                firmwareRequest = null;
//
//                GarminRequestManager.Companion.getRequestManager().requestFirmwareUpdates(device.address(), new FirmwareResultListener()
//                {
//
//                    @Override
//                    public void onSuccess(@Nullable List<FirmwareDownload> result)
//                    {
//                        if(result == null || result.size() == 0) {
//                            Log.i(LOG_TAG, "No firmware update available.");
//                            callback.onFirmwareUpdateNotAvailable();
//                            return;
//                        }
//
//                        Log.i(LOG_TAG, "Firmware update available. List of updatable components following:");
//                        for(FirmwareDownload download : result)  {
//                            Log.i(LOG_TAG, "  Updatable component: " + download.type() + " / Downloaded from: " + download.remoteUrl());
//                        }
//
//                        firmwareRequest = SettableFuture.create();
//                        firmwareRequest.set(result);
//                        firmwareRequestDeviceAddr = device.address();
//                        callback.onFirmwareUpdateAvailable();
//                    }
//
//                    @Override
//                    public void onError(@NonNull FirmwareErrorCode code, @NonNull Throwable t)
//                    {
//                        callback.onError(t);
//                        firmwareRequest = null;
//                    }
//                });
//
//            }
//            catch(Exception e)
//            {
//                callback.onError(e);
//            }
//        }).start();
//    }
//
//    @Override
//    public void installFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback) {
//        IllegalStateException noConnectedDeviceException = new IllegalStateException("No connected Garmin sensor device.");
//        Device device = getPairedGarminDevice();
//        if (device == null || device.connectionState() != ConnectionState.CONNECTED) {
//            callback.onError(noConnectedDeviceException);
//            return;
//        }
//        if(firmwareRequest == null || !device.address().equals(firmwareRequestDeviceAddr)) {
//            callback.onError(new IllegalStateException("Firmware not available or incompatible. Please call checkForFirmwareUpdate()."));
//            return;
//        }
//
//        new Thread(() ->
//        {
//            try
//            {
//                List<FirmwareDownload> downloads = firmwareRequest.get();
//
//                for(FirmwareDownload download : downloads)
//                {
//                    device.queueNewFirmware(download);
//                    Log.i(LOG_TAG, "Updated Device File: " + download.remoteUrl());
//                }
//
//                Log.i(LOG_TAG, "Device firmware update will be processed on next sensor sync.");
//
//                callback.onFirmwareUpdateQueued();
//
//            }
//            catch(Exception e)
//            {
//                callback.onError(e);
//            }
//        }).start();
//    }
//
//    private void downloadAndStoreLoggedData() {
//        String deviceAddress = getPairedGarminDevice().address();
//        String lastSyncTimePrefId = getContext().getString(R.string.lastGarminSensorSyncTime);
//
//        Date startTime = new Date(0);
//
//        try {
//            // per default, use begin of Study period as start time of download data period
//            // (if available)
//            startTime = Utils.setTimeToZero(
//                    Utils.getServerTimeFormat().parse(
//                            new JSONObject(BackendIO.getCurrentUser().anamnesisData)
//                                    .getString("study_begin_date")
//                    )
//            );
//        } catch (Exception ignored) { }
//
//
//        if (lastTimeSyncStored != null) {
//            // if available, use last time when data was downloaded as start time of download
//            // data period, if available and if it was after begin of current study period
//
//            if(lastTimeSyncStored.after(startTime))
//                startTime = lastTimeSyncStored;
//        }
//
//        Date now = new Date();
//        final long endTimeFinal = now.getTime() / 1000;
//        final long startTimeFinal = startTime.getTime() / 1000;
//
//        BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Download and store logged data from " + Utils.getServerTimeFormat().format(startTime) + " to " + Utils.getServerTimeFormat().format(now) + " from Garmin Device.");
//
//        try {
//            // Download data from device according to Garmin docs (DataLogging.html):
//            DeviceManager.getDeviceManager().hasLoggedData(deviceAddress, startTimeFinal, endTimeFinal, (hasData) ->
//            {
//                if(hasData == null || !hasData) {
//                    // Device has no data in SDK databases.
//                    BackendIO.serverLog(Log.WARN, LOG_TAG, "Sync with Garmin Device completed, but no logged data was available. Maybe not enough time has passed since the last synchronization. Otherwise, this is NOT NORMAL an should be further monitored!");
//                    return;
//                }
//
//                try {
//                    DeviceManager.getDeviceManager().getLoggedDataForDevice(deviceAddress, startTimeFinal, endTimeFinal, (loggingResult) ->
//                    {
//                        try {
//                            // Process logging data and store in local database.
//                            new GarminLogDataProcessor(loggingResult).storeLoggedGarminData();
//
//                            // Store last download time
//                            preferences.edit().putLong(lastSyncTimePrefId, now.getTime()).apply();
//                            lastTimeSyncStored = now;
//
//                            // UNCOMMENT FOLLOWING TWO LINES to delete device data after download, if necessary
//                            // delete downloaded data from device
//                            // boolean debug = DeviceManager.getDeviceManager().deleteData(deviceAddress, startTimeFinal, endTimeFinal);
//
//                        } catch (DataManager.NoPermissionException e) {
//                            Log.w(LOG_TAG, "Received Garmin Logging data was dismissed, since current user is either not logged in or not a participant.");
//                        } catch (Exception e) {
//                            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Processing downloaded Garmin logging data failed with unexpected exception: " + e);
//                        }
//                    });
//                } catch (Exception e) {
//                    BackendIO.serverLog(Log.ERROR, LOG_TAG, "Downloading Garmin logging data failed with unexpected exception: " + e);
//                }
//
//            });
//        } catch (Exception e) {
//            BackendIO.serverLog(Log.ERROR, LOG_TAG,"Checking for Garmin logged data failed with unexpected exception: " + e);
//        }
//    }
//
//
//    private class GarminSyncListener implements SyncListener, LoggingSyncListener {
//
//        private final SyncMode syncMode;
//
//        @Override
//        public void onSyncAuditComplete(Device device, boolean syncCompletelySuccessful, Callable<File> file) {
//
//            if (syncMode == SyncMode.LOGGING_DATA  || !loggingSyncActive ) {
//                // Workaround:
//                // There are multiple sync processes running in parallel by Garmin SDK, so
//                // even if onSyncComplete is called in one Listener, the whole sync process might
//                // not be completed yet.
//                // Therefore, if a sync of LOGGING data was started (indicated by loggingSyncActive flag),
//                // we will only feed back sync completed, when this process is done, since generally
//                // there is just one Logging sync process and it  will take more time than the other
//                // processes, so it will be finished at last
//                loggingSyncActive = false;
//
//                if(syncCompletelySuccessful) {
//                    BackendIO.serverLog(Log.DEBUG, LOG_TAG, "Garmin Sync Audit of Logging data completed. Processing results now.");
//                    downloadAndStoreLoggedData();
//                } else {
//                    BackendIO.serverLog(Log.ERROR, LOG_TAG, "Garmin Sync Audit of Logging data failed. (syncCompletelySuccessful = false)");
//                }
//
//                setSynchronizationFinished();
//            }
//        }
//
//        public GarminSyncListener(SyncMode syncMode) {
//            this.syncMode = syncMode;
//        }
//
//        @Override
//        public void onSyncStarted(Device device) {
//            if (syncMode == SyncMode.LOGGING_DATA) {
//                loggingSyncActive = true;
//            }
//            BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Synchronization of " + syncMode.toString() + " started." + " Sensor Device: " + device.friendlyName() + "/" + device.address());
//            setSynchronizationStarted();
//        }
//
//        @Override
//        public void onSyncComplete(Device device) {
//            BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Synchronization of " + syncMode.toString() + " completed." + " Sensor Device: " + device.friendlyName() + "/" + device.address());
//            // The completion of the sync process is handled in onSyncAuditComplete().
//
//        }
//
//        @Override
//        public void onSyncFailed(@NonNull Device device, @NonNull SyncException e) {
//            if (syncMode == SyncMode.LOGGING_DATA) {
//                loggingSyncActive = false;
//            }
//            BackendIO.serverLog(Log.WARN, LOG_TAG,"Synchronization of " + syncMode.toString() + " failed: " + e.toString() + " Sensor Device: " + device.friendlyName() + "/" + device.address());
//            setSynchronizationFailed(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : getContext().getString(R.string.unknown_error));
//        }
//
//        @Override
//        public void onSyncProgress(Device device, int progress) {
//            setSyncProgress(progress);
//        }
//    }
//
//    private final Utils.Observer<SensorSynchronizationState> updateConfigAfterSyncHandler = (object, synchronizationState) -> {
//        if(!synchronizationState.isSynchronizationActive()) {
//            if(configUpdateRequestedAfterSyncFinished) {
//                configUpdateRequestedAfterSyncFinished = false;
//                updateConfig();
//            }
//        }
//    };
//}
