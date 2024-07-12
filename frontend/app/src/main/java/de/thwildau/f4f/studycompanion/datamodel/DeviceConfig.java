package de.thwildau.f4f.studycompanion.datamodel;

import android.net.Uri;
import android.webkit.URLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class DeviceConfig {
    private List<SensorLoggingConfigData> garminLoggingConfig;
    private int serverSyncMaxAgeMinutes;
    private int sensorSyncMaxAgeMinutes;
    private int serverAutoSyncInterval;
    private int sensorAutoSyncInterval;
    private Uri apkDownloadUrl;
    private String garminLicenseKey;

    private List<String> sensorsUsed = Arrays.asList("Garmin", "Cosinuss");

    private String foodInputReminderTime = "2100";
    private String cosinussWearingReminderTime = "1930";
    private String cosinussWearingTimeBegin = "1900";
    private String cosinussWearingTimeEnd = "2200";
    private String cosinussWearingTimeDuration = "0045";

    private Integer serverApkVersionCode;


    public DeviceConfig() {
        //Create config using default values
        garminLoggingConfig = new ArrayList<>();
    }

    public DeviceConfig(String configDataJson) throws JSONException {
        JSONObject deviceConfigJson = new JSONObject(configDataJson);

        JSONObject garminLoggingJson = deviceConfigJson.getJSONObject("garmin_logging");

        Iterator<String> keys = garminLoggingJson.keys();

        garminLoggingConfig = new ArrayList<>();

        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject params = garminLoggingJson.getJSONObject(key);
            Integer interval = params.isNull("interval") ? null : params.getInt("interval");
            boolean enabled = params.getBoolean("enabled");

            garminLoggingConfig.add(new SensorLoggingConfigData(key, interval, enabled));
        }

        serverSyncMaxAgeMinutes = deviceConfigJson.getInt("server_sync_max_age_minutes");
        sensorSyncMaxAgeMinutes = deviceConfigJson.getInt("sensor_sync_max_age_minutes");
        serverAutoSyncInterval = deviceConfigJson.getInt("server_autosync_interval_minutes");
        sensorAutoSyncInterval = deviceConfigJson.getInt("sensor_autosync_interval_minutes");

        garminLicenseKey = deviceConfigJson.optString("garmin_license_key");

        String apkDownloadUrlStr = deviceConfigJson.optString("apk_download_url");
        if (URLUtil.isValidUrl(apkDownloadUrlStr)) {
            apkDownloadUrl = Uri.parse(apkDownloadUrlStr);
        } else {
            apkDownloadUrl = null;
        }

        foodInputReminderTime = deviceConfigJson.optString("food_input_reminder_time");
        cosinussWearingReminderTime = deviceConfigJson.optString("cosinuss_wearing_reminder_time");
        cosinussWearingTimeBegin = deviceConfigJson.optString("cosinuss_wearing_time_begin");
        cosinussWearingTimeEnd = deviceConfigJson.optString("cosinuss_wearing_time_end");
        cosinussWearingTimeDuration = deviceConfigJson.optString("cosinuss_wearing_time_duration");

        if(!deviceConfigJson.has("apk_version_code") || deviceConfigJson.isNull("apk_version_code")) {
            serverApkVersionCode = null;
        } else {
            serverApkVersionCode = deviceConfigJson.getInt("apk_version_code");
        }

        JSONArray sensorList = deviceConfigJson.optJSONArray("sensors_used");
        if(sensorList != null) { // preserve compatibility to API version <1. Use all sensor types per default.
            List<String> newSensors = new ArrayList<>(sensorList.length());
            for(int i = 0; i < sensorList.length(); i++) {
                String newSensor = sensorList.optString(i);
                if(!newSensor.isEmpty() && sensorsUsed.contains(newSensor))
                    newSensors.add(sensorList.getString(i));
            }
            sensorsUsed = newSensors;
        }
    }

    public List<SensorLoggingConfigData> getGarminLoggingConfig() {
        return garminLoggingConfig;
    }

    public int getServerSyncMaxAgeMinutes() {
        return serverSyncMaxAgeMinutes;
    }

    public int getSensorSyncMaxAgeMinutes() {
        return sensorSyncMaxAgeMinutes;
    }

    public int getServerAutoSyncInterval() {
        return serverAutoSyncInterval;
    }

    public int getSensorAutoSyncInterval() {
        return sensorAutoSyncInterval;
    }

    public Uri getApkDownloadUrl() {
        return apkDownloadUrl;
    }

    public String getGarminLicenseKey() {
        return garminLicenseKey;
    }

    public List<String> getSensorsUsed() {
        return sensorsUsed;
    }

    public boolean isGarminUsed() {
//        return sensorsUsed.contains("Garmin");
        return false; // substitute by upper line if Garmin support gets reintroduced
    }

    public boolean isCosinussUsed() {
        return sensorsUsed.contains("Cosinuss");
    }

    public String getFoodInputReminderTime() {
        return foodInputReminderTime;
    }

    public String getCosinussWearingReminderTime() {
        return cosinussWearingReminderTime;
    }

    public String getCosinussWearingTimeBegin() {
        return cosinussWearingTimeBegin;
    }

    public String getCosinussWearingTimeEnd() {
        return cosinussWearingTimeEnd;
    }

    public String getCosinussWearingTimeDuration() {
        return cosinussWearingTimeDuration;
    }

    public Integer getServerApkVersionCode() {
        return serverApkVersionCode;
    }

    public int getLowBatteryLevel() {
        return 20;
    }
}
