package de.thwildau.f4f.studycompanion.sensors.cosinuss;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import de.thwildau.f4f.studycompanion.sensors.GattAttributes;
import de.thwildau.f4f.studycompanion.sensors.SensorManagerBase;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorFirmwareUpdateProcessCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorPairingCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorScanCallback;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;

@SuppressLint("MissingPermission") // Seems the linter is not smart enough to recognize the dynamic permission requests in MainActivity

public class CosinussSensorManager extends SensorManagerBase {
    private static final String LOG_TAG = "CosinussSensorManager";

    private final Utils.ObservableValue<Integer> positioningQuality = new Utils.ObservableValue<>(null);
    private final Utils.ObservableValue<Integer> heartRate = new Utils.ObservableValue<>(null);
    private final Utils.ObservableValue<Float> bodyTemperature = new Utils.ObservableValue<>(null);

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private ScanCallback leScanCallback;
    private ISensorScanCallback scanCallback = null;
    private BluetoothLeScanner bluetoothLeScanner = null;
    private Long connectedTime = null;
    private boolean initialized = false;
    private final LinkedList<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<>();

    private BluetoothDevice pairingDevice = null;
    private ISensorPairingCallback pairingCallback = null;

    private final static String PREF_PAIRED_DEVICE_ADDR = "paired_cosinuss_one_addr";
    private final static String PREF_PAIRED_DEVICE_NAME = "paired_cosinuss_one_name";

    private final Map<ISensorDevice, BluetoothDevice> scannedDevices = new HashMap<>();

    private final CosinussDataRecorder cosinussDataRecorder = new CosinussDataRecorder();

    public CosinussSensorManager() {
        super();
    }

    @Override
    public void notifyBluetoothConnectionStateChange(BluetoothDevice btDevice, SensorConnectionState connectionState) {
        if (!initialized) {
            Log.i(LOG_TAG, "Bluetooth connection state change triggered initialization of CosinussSensorManager.");
            init();
            // The initialization routine will take care of registering a GATT connection request,
            // which will then automatically established everytime paired sensor is nearby.
            // This method is called by BluetoothConnectionStateBroadcastReceiver registered in manifest,
            // which helps trigger this initialization when app is not yet running
            // in background (e.g. after phone restart or app crash).

            // BUT actually the fact an action was sent to the BroadcastReceiver should trigger
            // a static initialization of the StudyCompanion (application) class,
            // which then calls the SensorManager's initialization routines,
            // so maybe this method implementation is redundant (could need further investigation).
        }
    }


    @Override
    public void init() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        registerBtBroadcastReceiver();
        assert bluetoothManager != null;
        bluetoothAdapter = bluetoothManager.getAdapter();

        registerBtBroadcastReceiver();

        getObservableConnectionState().removeObserver(connectionStateObserver);
        getObservableConnectionState().addObserver(connectionStateObserver, true);

        CosinussOneDevice pairedDevice = loadPairedDevice();

        if (bluetoothAdapter.isEnabled() && pairedDevice != null) {
            // Check if the bluetooth device bonding has manually been deleted by user
            BluetoothDevice pairedBtDevice = null;
            for (BluetoothDevice btDevice : bluetoothAdapter.getBondedDevices()) {
                if (pairedDevice.equals(btDevice)) {
                    pairedBtDevice = btDevice;
                    break;
                }
            }
            if (pairedBtDevice == null) {
                // Device not found in list of bonded devices; bonding was removed externally
                deleteStoredDevice();
                setCurrentDevice(null);
            } else {
                // Device address found in list of bonded devices.
                setCurrentDevice(pairedDevice);

                // Connect to Gatt server of bonded device as soon as it's available
                connectGatt(pairedBtDevice);
            }
        } else {
            setCurrentDevice(pairedDevice);
        }

        if(!initialized) {
            getObservableConnectionState().addObserver(notificationDisplayProvider);
            restoreLastSyncTime();
            cosinussDataRecorder.getObservableLastSyncTime().addObserver((object, lastSyncTime) -> updateLastSyncTime(lastSyncTime));
        }

        initialized = true;
    }

    private final Utils.Observer<SensorConnectionState> connectionStateObserver = (object, connectionState) -> {
        switch (connectionState) {
            case CONNECTED:
                connectedTime = System.currentTimeMillis();
                cosinussDataRecorder.setConnectedTime(connectedTime);
                break;
            case DISCONNECTED:
                NotificationOrganizer.hideCosinussWearingCompletedReminder();
                cosinussDataRecorder.setConnectedTime(0);
                connectedTime = null;
        }
    };

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    private boolean isNewFirmware(ISensorDevice device) {
        // We use the device name to determine firmware version.
        // Devices with the old firmware broadcast the name "earconnect",
        // the new broadcast "one".

        if(device == null) {
            BackendIO.serverLog(Log.WARN, LOG_TAG, "Invalid argument for isNewFirmware(device). device was null. Assuming old firmware.");
            return false;
        }

        return "one".equals(device.getName());
    }


    public int convertPositioningQuality(int rawQualityValue) {
        ISensorDevice device = getCurrentSensorDevice();
        if(device == null)
            return 0;

        // Interpretation of signal value differs depending on firmware version.
        // Old firmware specification says:
        //  "low values mean uncertain heart rate values, high ones (> 50) mean valid heart rate values."
        // New firmware specification says:
        //  "Values of 30 and above can be considered as good quality.
        //   Heart rate values with lower quality are likely to be incorrect."

        // But in the old firmware we discovered that the value very often is between 30 and 50,
        // even if the values look pretty plausible. That's why we introduced a "medium quality"
        // level for this range.

        // Since the specification for the new firmware is clearer about this, we left out this
        // medium quality, but define all values >= 30 as good and all below as bad quality.

        if(isNewFirmware(device)) { // NEW FIRMWARE
            if(rawQualityValue >= 30)
                // assume good quality
                return 100;

            // assume bad quality (scale return value < 50)
            return (int)(1.72 * rawQualityValue);

        } else { // OLD FIRMWARE

            if(rawQualityValue > 50)
                // assume good quality
                return 100;
            else if(rawQualityValue >= 30)
                // scale return value to be between 50 and 99
                return 50 + (int)((rawQualityValue - 30) * 2.45);
            else
                // scale return value to be between 0 and 49
                return (int)(1.66 * rawQualityValue);
        }
    }


    @Override
    public void startScanningForDevicesImplementation(@NonNull ISensorScanCallback callback) {
        if (bluetoothLeScanner != null) {
            stopScanningForDevicesImplementation();
        }

        scannedDevices.clear();

        if(!bluetoothAdapter.isEnabled()) {
            callback.onScanFailed(getContext().getString(R.string.error_bluetooth_disabled));
            return;
        }

        scanCallback = callback;

        leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice bluetoothDevice = result.getDevice();
                ISensorDevice sensorDevice = new CosinussOneDevice(bluetoothDevice.getName(), bluetoothDevice.getAddress());
                if (!scannedDevices.containsValue(bluetoothDevice) && !Utils.nullOrEmpty(bluetoothDevice.getName())) {
                    scannedDevices.put(sensorDevice, bluetoothDevice);
                    callback.onScannedDevice(sensorDevice);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                callback.onScanFailed("BLE scan failed. (Error Code: " + errorCode + ")");
                bluetoothLeScanner = null;
            }

        };
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(new ScanFilter.Builder().setDeviceName("earconnect").build());
        scanFilters.add(new ScanFilter.Builder().setDeviceName("one").build());
        bluetoothLeScanner.startScan(scanFilters, new ScanSettings.Builder().build(), leScanCallback);
        Log.d(LOG_TAG, "BLE Scanning started.");
    }

    @Override
    public void stopScanningForDevicesImplementation() {
        if (bluetoothLeScanner != null) {
            if(bluetoothAdapter.isEnabled()) {
                bluetoothLeScanner.flushPendingScanResults(leScanCallback);
                bluetoothLeScanner.stopScan(leScanCallback);
                Log.d(LOG_TAG, "BLE Scanning stopped.");
            }
            bluetoothLeScanner = null;
        }
    }

    @Override
    public void unpairDevice() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();  // might not be connected, but in case BT Framework will just log a warning, not throw an exception
            bluetoothGatt.close();
            bluetoothGatt = null;
            setConnectionState(SensorConnectionState.DISCONNECTED);
        }
        ISensorDevice currentDevice = getCurrentSensorDevice();
        if (currentDevice == null) {
            return;
        }

        // Try to un-bond device from System. It is not a problem, if it fails
        for (BluetoothDevice pairedBtDevice : bluetoothAdapter.getBondedDevices()) {
            if (((CosinussOneDevice) getCurrentSensorDevice()).equals(pairedBtDevice)) {
                try {
                    Method m = pairedBtDevice.getClass()
                            .getMethod("removeBond", (Class[]) null);
                    m.invoke(pairedBtDevice, (Object[]) null);
                } catch (Exception e) {
                    e.printStackTrace();
                    // ignore
                }
            }
        }

        BackendIO.serverLog(Log.INFO, LOG_TAG, "Unpaired Cosinuss Device with MAC Address: " + currentDevice.getMacAddress());
        setCurrentDevice(null);
        deleteStoredDevice();
        if (pairingDevice != null) {
            // interrupt ongoing pairing process
            pairingDevice = null;
            if (pairingCallback != null) {
                pairingCallback.onPairingCancelled();
            }
        }
    }

    @Override
    public ISensorDevice getCurrentSensorDevice() {
        return getObservableCurrentDevice().getValue();
    }

    @Override
    public void pairWithDevice(ISensorDevice device, ISensorPairingCallback callback) {
        if (!(device instanceof CosinussOneDevice)) {
            throw new IllegalArgumentException("CoinussSensorManager can only pair with CosinussOneDevice.");
        }

        BluetoothDevice btDevice = scannedDevices.get(device);

        if (btDevice == null) {
            throw new IllegalArgumentException("Trying to pair with a device which was not scanned during last scanning process.");
        }

        if (getCurrentSensorDevice() != null) {
            unpairDevice();
        }

        pairingDevice = btDevice;
        pairingCallback = callback;

        if (pairingDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            pairingDevice.createBond();
            // pairing device will only be set and stored as currently paired device,
            // after bonding completed AND connection go GATT could be successfully established
            // for the first time
        } else {
            connectGatt(pairingDevice);
        }
    }

    private void restoreLastSyncTime() {
        SharedPreferences prefs = StudyCompanion.getUserPreferences();
        if(prefs == null) {
            return;
        }
        String prefName = getContext().getString(R.string.lastCosinussSensorConnectionTime);

        long lastConnectionTime = prefs.getLong(prefName, 0);
        if(lastConnectionTime != 0) {
            setLastSyncTime(new Date(lastConnectionTime));
        }
    }

    private void updateLastSyncTime(Date lastSyncTime) {
        SharedPreferences prefs = StudyCompanion.getUserPreferences();
        if(prefs == null) {
            return;
        }
        String prefName = getContext().getString(R.string.lastCosinussSensorConnectionTime);
        setLastSyncTime(lastSyncTime);

        prefs.edit().putLong(prefName, lastSyncTime.getTime()).apply();
    }




    private void storePairedDevice(CosinussOneDevice device) {
        StudyCompanion.getGlobalPreferences().edit()
                .putString(PREF_PAIRED_DEVICE_ADDR, device.getMacAddress())
                .putString(PREF_PAIRED_DEVICE_NAME, device.getName())
                .apply();
    }

    private void deleteStoredDevice() {
        StudyCompanion.getGlobalPreferences().edit()
                .remove(PREF_PAIRED_DEVICE_ADDR)
                .remove(PREF_PAIRED_DEVICE_NAME)
                .apply();
    }

    private CosinussOneDevice loadPairedDevice() {
        SharedPreferences sp = StudyCompanion.getGlobalPreferences();
        String deviceAddr = sp.getString(PREF_PAIRED_DEVICE_ADDR, null);
        String deviceName = sp.getString(PREF_PAIRED_DEVICE_NAME, null);
        if (deviceAddr == null) {
            return null;
        }

        return new CosinussOneDevice(deviceName, deviceAddr);
    }


    private void connectGatt(BluetoothDevice bluetoothDevice) {
        bluetoothGatt = bluetoothDevice.connectGatt(getContext(), true, bluetoothGattCallback);
    }

    @Override
    public void cancelPairing() {
        pairingDevice = null;
        if (pairingCallback != null) {
            pairingCallback.onPairingCancelled();
        }
    }

    @Override
    public void startSynchronization() {

    }

    @Override
    public void updateConfig() {

    }

    @Override
    public void checkForFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback) {

    }

    @Override
    public void installFirmwareUpdate(ISensorFirmwareUpdateProcessCallback callback) {

    }

    @Override
    public void acquireCurrentBatteryLevel() {

    }

    /**
     * A in-ear positioning quality value defined as follows:
     *  100: Good quality
     *  50-99: Medium quality
     *  0-49: Bad quality
     * @return
     */
    public Utils.ObservableValue<Integer> getObservablePositioningQuality() {
        return positioningQuality;
    }

    public Utils.ObservableValue<Integer> getObservableHeartRate() {
        return heartRate;
    }

    public Utils.ObservableValue<Float> getObservableBodyTemperature() {
        return bodyTemperature;
    }

    private void registerBtBroadcastReceiver() {
        if (initialized) {
            return;
        }
        getContext().registerReceiver(bluetoothBondStateReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        getContext().registerReceiver(bluetoothEnabledStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private final BroadcastReceiver bluetoothBondStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                return;
            }

            BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (btDevice == null) {
                return;
            }

            CosinussOneDevice currentDevice = getCurrentSensorDevice() != null ? ((CosinussOneDevice) getCurrentSensorDevice()) : null;
            int newBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
            int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, 0);

            if (pairingDevice != null && pairingDevice.equals(btDevice) && newBondState == BluetoothDevice.BOND_BONDED) {
                connectGatt(btDevice);
            }

            if (pairingDevice != null && pairingDevice.equals(btDevice) && newBondState == BluetoothDevice.BOND_NONE && previousBondState == BluetoothDevice.BOND_BONDING) {
                if (pairingCallback != null) {
                    pairingCallback.onPairingFailed("Bonding process interrupted.");
                }
                pairingDevice = null;
            }

            if (currentDevice != null && currentDevice.equals(btDevice) && newBondState == BluetoothDevice.BOND_NONE) {
                // bonding was manually removed by user, so we disconnect and forget the device
                unpairDevice();
            }

        }
    };

    private final BroadcastReceiver bluetoothEnabledStateReceiver =  new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }

            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            ISensorDevice currentSensorDevice = getCurrentSensorDevice();

            if(state == BluetoothAdapter.STATE_ON && currentSensorDevice != null) {
                BluetoothDevice pairedBtDevice = null;
                CosinussOneDevice pairedDevice = (CosinussOneDevice)currentSensorDevice;

                if (pairedDevice != null) {
                    // Find the corresponding Bluetooth device in the list of bonded devices.
                    for (BluetoothDevice btDevice : bluetoothAdapter.getBondedDevices()) {
                        if (pairedDevice.equals(btDevice)) {
                            pairedBtDevice = btDevice;
                            break;
                        }
                    }
                }

                if(pairedBtDevice != null) {
                    connectGatt(pairedBtDevice);
                }
            } else if(
                    state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF
            )  {
                if (bluetoothGatt != null) {
                    // Release bluetooth connection
                    try {
                        bluetoothGatt.disconnect();  // might not be connected, but in case BT Framework will just log a warning, not throw an exception
                        bluetoothGatt.close();
                    } catch(Throwable e) {
                        // ignore
                    }
                    bluetoothGatt = null;
                }
                setConnectionState(SensorConnectionState.DISCONNECTED);
                if (bluetoothLeScanner != null)
                    scanCallback.onScanFailed(getContext().getString(R.string.error_bluetooth_disabled));
                bluetoothLeScanner = null;
            }
        }
    };

    private void writeQueuedDescriptors() {
        if (descriptorWriteQueue.isEmpty()) {
            return;
        }

        BluetoothGattDescriptor descriptor = descriptorWriteQueue.remove();
        bluetoothGatt.writeDescriptor(descriptor);
    }


    private void enableNotifications(BluetoothGattCharacteristic characteristic) {
        // This enables notifications OR indications (if no notification available) for the given characteristic

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GattAttributes.CHARACTERISTIC_CONFIG_DESCRIPTOR));
        if (descriptor == null) {
            // This characteristic is not configurable.
            return;
        }

        byte[] descriptorValue;
        int charProperties = characteristic.getProperties();

        if ((charProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((charProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
            descriptorValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            // this characteristic does not allow indications or notifications
            // Don't do nothing.
            return;
        }

        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        descriptor.setValue(descriptorValue);
        descriptorWriteQueue.add(descriptor);
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothGatt.STATE_CONNECTED:
                    if (pairingDevice != null && pairingDevice.equals(gatt.getDevice())) {
                        // Connection established during ongoing pairing process.
                        // This is the last phase where this process is completed and the paired devices is stored.
                        CosinussOneDevice currentDevice = new CosinussOneDevice(pairingDevice.getName(), pairingDevice.getAddress());
                        pairingDevice = null;
                        storePairedDevice(currentDevice);
                        setCurrentDevice(currentDevice);
                        BackendIO.serverLog(Log.INFO, LOG_TAG, "Paired new Cosinuss Device with MAC Address: " + currentDevice.getMacAddress());
                        if (pairingCallback != null) {
                            pairingCallback.onPairingSucceeded();
                        }
                    }
                    setConnectionState(SensorConnectionState.CONNECTED);
                    BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Cosinuss Sensor connected.");
                    gatt.discoverServices();
                    break;
                case BluetoothGatt.STATE_DISCONNECTED:
                    setConnectionState(SensorConnectionState.DISCONNECTED);
                    BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Cosinuss Sensor disconnected.");
                    break;
                case BluetoothGatt.STATE_CONNECTING:
                    Log.d(LOG_TAG, "Cosinuss Sensor GATT connecting...");
                    setConnectionState(SensorConnectionState.CONNECTING);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            for (BluetoothGattService service : gatt.getServices()) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    if (GattAttributes.isRelevantCharacteristic(characteristic)) {
                        enableNotifications(characteristic);
                    }
                }
            }
            writeQueuedDescriptors();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            writeQueuedDescriptors();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            ISensorDevice device = getCurrentSensorDevice();
            boolean newFirmware = isNewFirmware(device);

            if(newFirmware)
                cosinussDataRecorder.setIsNewFirmware();

            // All sensor values are actively sent by the sensor via notification or indication
            // causing this method to be called.

            byte[] value = characteristic.getValue();
            UUID uuid = characteristic.getUuid();
            boolean receivedData = true;

            if (GattAttributes.equals(uuid, GattAttributes.SIGNAL_QUALITY)) {

                // How to extract the signal quality value depends on the firmware version.
                // In the old firmware, the signal quality can just be read from value[0].
                // In the new firmware value[0] muts be 0x06 and the value is read from value[8].
                // see: https://wiki.earconnect.de/public/ble?do=

                int positioningQualityInt = 0;
                boolean validValue = true;
                if(newFirmware) {
                    if (value[0] != 0x06) {
                        validValue = false;
                    } else {
                        positioningQualityInt = value[8];
                    }
                } else {
                    positioningQualityInt = value[0];
                }

                if(validValue) {
                    positioningQuality.setValue(convertPositioningQuality(positioningQualityInt));
                    cosinussDataRecorder.addPositioningQuality(positioningQualityInt);
                }

            } else if (GattAttributes.equals(uuid, GattAttributes.HEART_RATE_MEASUREMENT)) {
                CosinussHeartMeasurement hrm = CosinussHeartMeasurement.FromCharacteristicValue(value);
                cosinussDataRecorder.addHeartRateMeasurement(hrm);
                heartRate.setValue(hrm.getBpm());

            } else if (GattAttributes.equals(uuid, GattAttributes.TEMPERATURE_MEASUREMENT)) {
                // value[0]: Flags, see: https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.temperature_measurement.xml
                // cosinuss One always returns flags 0b00000100 => Measured Temperature in Celsius and extra Temperature Type Field available.
                // value[1:4]: Measured Temperature as ISO/IEEE 11073-20601-2008 conforming FLOAT (32-Bit),  Little Endian
                // value[5]: Temperature Type (should always be 3 for ear measurement, see https://github.com/philngo/ble-mathematica/blob/master/BLEAutoConnect/BLEAutoConnect/GATTSpecifications/characteristics/org.bluetooth.characteristic.temperature_type.xml)
                byte[] temperatureBytes = {value[1], value[2], value[3], value[4]};
                float temperature = Utils.convertFromISO11073_20601_32Bit(temperatureBytes);
                cosinussDataRecorder.addTemperatureValue(temperature);
                bodyTemperature.setValue(temperature);

            } else if (GattAttributes.equals(uuid, GattAttributes.BATTERY_LEVEL)) {
                getObservableBatteryLevel().setValue((int) value[0]);
            } else {
                receivedData = false;
            }

            long timeCompletedDelayMs = Utils.getMinutesFromMilitaryTimeDuration(SchemaProvider.getDeviceConfig().getCosinussWearingTimeDuration()) * 60 * 1000L;

            if(timeCompletedDelayMs > 0 && receivedData && connectedTime != null) {
                long timePassedMs = System.currentTimeMillis() - connectedTime;
                if(timePassedMs > timeCompletedDelayMs) {
                    connectedTime = null;
                    NotificationOrganizer.showCosinussWearingCompletedReminder();
                }
            }
        }
    };


    private final Utils.Observer<SensorConnectionState> notificationDisplayProvider = (object, connectionState) -> {
        if(connectionState == SensorConnectionState.CONNECTED) {
            NotificationOrganizer.showCosinussNotification(null, null, null, connectedTime == null ? 0 : connectedTime);
            NotificationOrganizer.hideCosinussReminder();
        } else {
            NotificationOrganizer.hideCosinussNotification();
        }
    };

}
