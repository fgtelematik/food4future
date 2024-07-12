//package de.thwildau.f4f.studycompanion.sensors.garmin;
//
//import android.util.Log;
//
//import com.garmin.health.customlog.LegacyLoggingResult;
//import com.garmin.health.database.dtos.HeartRateLog;
//import com.garmin.health.database.dtos.HeartRateVariabilityLog;
//import com.garmin.health.database.dtos.PulseOxLog;
//import com.garmin.health.database.dtos.RawAccelerometerLog;
//import com.garmin.health.database.dtos.RawAccelerometerSample;
//import com.garmin.health.database.dtos.RespirationLog;
//import com.garmin.health.database.dtos.StepLog;
//import com.garmin.health.database.dtos.StressLog;
//import com.garmin.health.database.dtos.ZeroCrossingLog;
//
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.util.LinkedList;
//import java.util.List;
//
//import de.thwildau.f4f.studycompanion.backend.BackendIO;
//import de.thwildau.f4f.studycompanion.datamodel.DataManager;
//import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;
//import de.thwildau.f4f.studycompanion.datamodel.enums.SensorDataType;
//
//public class GarminLogDataProcessor {
//
//    private static final String LOG_TAG = "GarminLogData";
//
//    private final LegacyLoggingResult loggingResult;
//    private final List<JSONObject> newSensorData;
//
//
//    GarminLogDataProcessor(LegacyLoggingResult loggingResult) {
//        this.loggingResult = loggingResult;
//        this.newSensorData = new LinkedList<>();
//    }
//
//    private void extractHeartRates() throws JSONException {
//
//        List<HeartRateLog> heartRateList = loggingResult.getHeartRateList();
//        if (heartRateList != null && heartRateList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//
//            JSONArray timestamps = new JSONArray();
//            JSONArray values = new JSONArray();
//            JSONArray status = new JSONArray();
//
//            for (HeartRateLog entry : heartRateList) {
//                timestamps.put(entry.getTimestamp() * 1000); //TODO: Check, if timestamp is really in seconds!
//                values.put(entry.getHeartRate());
//                status.put(entry.getStatus().toString());
//                entry.getStatus();
//            }
//
//            dataset.put("type", SensorDataType.Pulse.toString());
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("status", status); //TODO: not part of specification (yet)! Verify significance of this information
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + heartRateList.size() + " HeartRate datasets from logged data.");
//        }
//    }
//
//    private void extractHrvs() throws JSONException {
//        List<HeartRateVariabilityLog> hrvList = loggingResult.getHrvList();
//        if (hrvList != null && hrvList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (HeartRateVariabilityLog entry : hrvList) {
//                timestamps.put(entry.getTimestampMs());
//                values.put(entry.getBeatBeatInterval());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("type", SensorDataType.HRV.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + hrvList.size() + " HRV datasets from logged data.");
//        }
//    }
//
//    private void extractRespiration() throws JSONException {
//        List<RespirationLog> respirationList = loggingResult.getRespirationList();
//        if (respirationList != null && respirationList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (RespirationLog entry : respirationList) {
//                timestamps.put(entry.getTimestamp() * 1000);
//                values.put(entry.getRespirationValue());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("type", SensorDataType.Respiration.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + respirationList.size() + " Respiration datasets from logged data.");
//        }
//    }
//
//    private void extractSteps() throws JSONException {
//        List<StepLog> stepList = loggingResult.getStepList();
//        if (stepList != null && stepList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray timespans = new JSONArray();
//            JSONArray durations = new JSONArray();
//            JSONArray totalStepsList = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (StepLog entry : stepList) {
//                long timestamp = entry.getEndTimestamp();
//                long timespan = timestamp - entry.getStartTimestamp();
//                long duration = entry.getDuration(); //TODO: Check if timespan == duration!
//                long totalSteps = entry.getTotalSteps(); //TODO: Use this value?
//
//                timestamps.put(timestamp * 1000);
//                timespans.put(timespan * 1000);
//                durations.put(duration);
//                totalStepsList.put(totalSteps);
//
//
//                values.put(entry.getStepCount());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("time_spans", timespans);
//            dataset.put("type", SensorDataType.StepCount.toString());
//            dataset.put("durations_raw", durations);
//            dataset.put("total_steps_raw", totalStepsList);
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + stepList.size() + " StepCount datasets from logged data.");
//        }
//    }
//
//    private void extractStress() throws JSONException {
//        List<StressLog> stressList = loggingResult.getStressList();
//        if (stressList != null && stressList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (StressLog entry : stressList) {
//                timestamps.put(entry.getTimestamp() * 1000);
//                values.put(entry.getStressScore());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("type", SensorDataType.StressScore.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + stressList.size() + " StressScore datasets from logged data.");
//        }
//    }
//
//    private void extractPulseOx() throws JSONException {
//        List<PulseOxLog> pulseOxList = loggingResult.getPulseOxList();
//        if (pulseOxList != null && pulseOxList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (PulseOxLog entry : pulseOxList) {
//                timestamps.put(entry.getTimestamp() * 1000);
//                values.put(entry.getPulseOx());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("type", SensorDataType.PulseOx.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + pulseOxList.size() + " PulseOx datasets from logged data.");
//        }
//    }
//
//    private void extractAccelerometer() throws JSONException {
//        List<RawAccelerometerLog> accelerometerList = loggingResult.getRawAccelerometerList();
//        if (accelerometerList != null && accelerometerList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray xVals = new JSONArray();
//            JSONArray yVals = new JSONArray();
//            JSONArray zVals = new JSONArray();
//            JSONArray msFractions = new JSONArray();
//
//            long count = 0L;
//
//            for (RawAccelerometerLog entry : accelerometerList) {
//                List<RawAccelerometerSample> sampleList = entry.getRawAccelerometerSampleList();
//                for (RawAccelerometerSample sample : sampleList) {
//                    count++;
//                    timestamps.put(sample.getTimestampMs());
//                    xVals.put(sample.getX());
//                    yVals.put(sample.getY());
//                    zVals.put(sample.getZ());
//                    msFractions.put(sample.getMillisecondFraction());
//                }
//            }
//
//            dataset.put("x", xVals);
//            dataset.put("y", yVals);
//            dataset.put("z", zVals);
//            dataset.put("timestamps", timestamps);
//            dataset.put("ms_fractions", msFractions);  //TODO: not part of specification (yet)! Verify significance of this information
//            dataset.put("type", SensorDataType.Acceleration.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + count + " Acceleration datasets from logged data.");
//        }
//    }
//
//    private void extractEnergy() throws JSONException {
//        List<ZeroCrossingLog> energyList = loggingResult.getZeroCrossingList();
//        if (energyList != null && energyList.size() > 0) {
//            JSONObject dataset = new JSONObject();
//            JSONArray timestamps = new JSONArray();
//            JSONArray timespans = new JSONArray();
//            JSONArray zerocrossings = new JSONArray();
//            JSONArray values = new JSONArray();
//
//            for (ZeroCrossingLog entry : energyList) {
//                timestamps.put(entry.getTimestamp() * 1000);
//                timespans.put(entry.getTimeElapsed() * 1000);
//                values.put(entry.getEnergyTotal());
//                zerocrossings.put(entry.getZeroCrossingCount());
//            }
//
//            dataset.put("values", values);
//            dataset.put("timestamps", timestamps);
//            dataset.put("time_spans", timespans);
//            dataset.put("zero_crossings", zerocrossings);
//            dataset.put("type", SensorDataType.Energy.toString());
//
//            newSensorData.add(dataset);
//
//            Log.v(LOG_TAG, "Extracted " + energyList.size() + " Energy/ZeroCrossing datasets from logged data.");
//        }
//    }
//
//
//    public void storeLoggedGarminData() throws DataManager.NoPermissionException {
//        try {
//            BackendIO.serverLog(Log.INFO, LOG_TAG,"Started conversion of logged Garmin datasets.");
//            extractAccelerometer();
//            extractEnergy();
//            extractHeartRates();
//            extractHrvs();
//            extractPulseOx();
//            extractRespiration();
//            extractSteps();
//            extractStress();
//            BackendIO.serverLog(Log.INFO, LOG_TAG,"Finished conversion of logged Garmin datasets.");
//        } catch (JSONException e) {
//            BackendIO.serverLog(Log.ERROR, LOG_TAG,"An error occurred while converting logged datasets: " + e.getLocalizedMessage());
//            e.printStackTrace();
//        }
//
//        Log.d(LOG_TAG, "Storing converted sensor data in local database.");
//
//        for(JSONObject dataset : newSensorData) {
//            DataManager.updateOrInsertData(DataType.SensorData, dataset);
//        }
//
//        Log.d(LOG_TAG, "Finished storing converted sensor data in local database.");
//    }
//}
