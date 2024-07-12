package de.thwildau.f4f.studycompanion.sensors.cosinuss;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;

public class CosinussDataRecorder {
    private static final String LOG_TAG = "CosinussDataRecorder";

    private static final int COMBINED_DATA_TIME_WINDOW_MS = 1000;

    // when one of these limits is reached, current cache will be transformed into a SensorData dataset
    // and stored in local Realm TODO: Make limits a server-side configurable setting!
    private static final int CACHE_MAX_AGE_SECONDS = 60;
    private static final int CACHE_MAX_SIZE = 60;

    private List<Dataset> cache = new LinkedList<>();
    private final Object cacheLock = new Object();
    private Dataset currentDataset = new Dataset();
    private Long cacheInitializationTime = null;

    private final Utils.ObservableValue<Date> lastSyncTime = new Utils.ObservableValue<>();
    private long connectedTime = 0;

    private static class Dataset {
        private Float temperature;
        private CosinussHeartMeasurement hrm;
        private Integer positioningQuality;
        private Long timestamp;
        private boolean isNewFirmware = false;

        private Dataset() {
            reset();
        }

        private void reset() {
            temperature = null;
            hrm = null;
            positioningQuality = null;
            timestamp = System.currentTimeMillis();
            isNewFirmware = false;
        }
    }



    private void tryStoreCurrentCache() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (cacheInitializationTime == null) {
                cacheInitializationTime = System.currentTimeMillis();

                // Check for outdated cache asynchronously
                new Handler(Looper.getMainLooper()).postDelayed(
                        this::tryStoreCurrentCache, CACHE_MAX_AGE_SECONDS * 1000 + 10);
            }

            if (
                    now - cacheInitializationTime > CACHE_MAX_AGE_SECONDS * 1000 ||
                            cache.size() > CACHE_MAX_SIZE
            ) {
                Log.d(LOG_TAG, "Cache limit reached. Storing cache in local Realm.");
                // current cache has reached max age or size
                // create new cache and store old one
                List<Dataset> completedCache = cache;
                cacheInitializationTime = null;
                cache = new LinkedList<>();
                storeCacheAsync(completedCache);
            }
        }
    }

    private void storeCacheAsync(List<Dataset> cache) {
        if (
                cache == null
                        // Might there be a chance for this case, if tryStoreCurrentCache() was called
                        // asynchronously by local outdated cache Handler and resources of current class instance
                        // were already released by Garbage Collector?

                        || cache.size() == 0
        ) {
            return;
        }

        new Thread(() -> {
            // Create two SensorData JSONObjects (typed HeartMeasurementEar and Temperature)
            // and store them in local Realm in Background

            try {
                JSONObject heartRateData = new JSONObject();
                heartRateData.put("type", "HeartMeasurementEar");

                JSONObject temperatureData = new JSONObject();
                temperatureData.put("type", "Temperature");

                JSONArray heartRateBPM = new JSONArray();
                JSONArray heartRateRRlists = new JSONArray();
                JSONArray temperatures = new JSONArray();
                JSONArray timestamps = new JSONArray();
                JSONArray poisitioningQualityValues = new JSONArray();

                boolean validDatasets = false;
                boolean isNewFirmware = false;

                for (Dataset dataset : cache) {
                    if (dataset.hrm.getBpm() <= 20) {
                        // reject whole dataset, if no realistic BPM value was be measured
                        // Miguel Indurain holds world record for the slowest human heart rate ever measured with 28 BPM! ;-)
                        continue;
                    }

                    // generate RR sublist for this dataset
                    JSONArray rrListArray = new JSONArray();
                    List<Float> rrList = dataset.hrm.getRRValues();
                    for (Float rrValue : rrList) {
                        rrListArray.put(rrValue);
                    }

                    // append dataset parts to JSON arrays
                    heartRateBPM.put(dataset.hrm.getBpm());
                    heartRateRRlists.put(rrListArray);
                    temperatures.put(dataset.temperature);
                    timestamps.put(dataset.timestamp);
                    poisitioningQualityValues.put(dataset.positioningQuality);

                    isNewFirmware = dataset.isNewFirmware;
                        // only the value of the last dataset is used here, since it should be equal for all

                    validDatasets = true;
                }

                if (!validDatasets) {
                    // no valid data recorded
                    return;
                }

                heartRateData.put("values", heartRateBPM);
                heartRateData.put("rr_history", heartRateRRlists);
                temperatureData.put("values", temperatures);

                heartRateData.put("timestamps", timestamps);
                temperatureData.put("timestamps", timestamps);
                heartRateData.put("ear_position_quality", poisitioningQualityValues);
                temperatureData.put("ear_position_quality", poisitioningQualityValues);

                temperatureData.put("new_firmware", isNewFirmware);
                heartRateData.put("new_firmware", isNewFirmware);

                // Store data in local Realm
                DataManager.updateOrInsertData(DataType.SensorData, heartRateData);
                DataManager.updateOrInsertData(DataType.SensorData, temperatureData);

                Log.d(LOG_TAG, "Stored " + cache.size() + " records in in Realm (split in one HeartMeasurementEar and one Temperature SensorData data set).");
                lastSyncTime.setValue(new Date());
            } catch(DataManager.NoPermissionException e) {
                // This is a rare case and happens only, if user was logged out during processing the upper try{]-block.
                Log.w(LOG_TAG, "Collected Cosinuss sensor data were dismissed, since current user is either not logged in or not a participant.");
            } catch (Exception e) {
                // shouldn't actually happen
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                BackendIO.serverLog(Log.ERROR, LOG_TAG,"ERROR processing In-Ear datasets: " + e + "\n  Stack Trace: " + sw);
            }
        }).start();
    }

    private void tryAddCurrentDatasetToCache() {

        try {
            // check, if current user is logged in as a participant
            DataManager.checkPermission();
        } catch (DataManager.NoPermissionException e) {
            // Dismiss collected data set, if user is not a participant
            return;
        }

        // Add this dataset to the cache list when all three sensor values are set within a time window of COMBINED_DATA_DELAY_MS
        long now = System.currentTimeMillis();

        if (currentDataset.temperature != null &&
                currentDataset.positioningQuality != null &&
                currentDataset.hrm != null
        ) {
            synchronized (cacheLock) {
                Log.d(LOG_TAG, "Current in-ear data set completed and cached:" +
                                "\n  HeartRateMeasurement: " + currentDataset.hrm.toString() +
                                "\n  PositioningQuality: " + currentDataset.positioningQuality +
                                "\n  Temperature: " + currentDataset.temperature +
                                "\n  Timestamp: " + currentDataset.timestamp +
                                "\n  New Firmware: " + currentDataset.isNewFirmware );
                showNotification();
                cache.add(currentDataset);
                currentDataset = new Dataset();
            }

            tryStoreCurrentCache();
            return;
        }

        if (now - currentDataset.timestamp > COMBINED_DATA_TIME_WINDOW_MS) {
            currentDataset.reset();
        }
    }

    private void showNotification() {
        NotificationOrganizer.showCosinussNotification(currentDataset.hrm.getBpm() == 0 ? null : currentDataset.hrm.getBpm(), currentDataset.temperature, currentDataset.positioningQuality, connectedTime);
    }

    public void refreshNotification() {
        new Thread(() -> {
            synchronized (cacheLock) {
                new Handler(Looper.getMainLooper()).post(this::showNotification);
            }
        }).start();
    }

    public void addTemperatureValue(float temperatureValue) {
        tryAddCurrentDatasetToCache();
        currentDataset.temperature = temperatureValue;
    }

    public void addHeartRateMeasurement(CosinussHeartMeasurement hrmValue) {
        tryAddCurrentDatasetToCache();
        currentDataset.hrm = hrmValue;
    }

    public void addPositioningQuality(int positioningQualityValue) {
        tryAddCurrentDatasetToCache();
        currentDataset.positioningQuality = positioningQualityValue;
    }

    public void setIsNewFirmware() {
        currentDataset.isNewFirmware = true;
    }

    public void setConnectedTime(long connectedTime) {
        this.connectedTime = connectedTime;
    }

    public Utils.ObservableValue<Date> getObservableLastSyncTime() {
        return lastSyncTime;
    }
}
