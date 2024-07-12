//package de.thwildau.f4f.studycompanion.sensors.garmin;
//
//import android.util.Log;
//
//import com.garmin.health.Device;
//import com.garmin.health.Failure;
//import com.garmin.health.Success;
//import com.garmin.health.customlog.LoggingConfiguration;
//import com.garmin.health.customlog.LoggingType;
//import com.google.common.util.concurrent.Futures;
//
//import java.util.List;
//import java.util.Objects;
//
//import de.thwildau.f4f.studycompanion.backend.BackendIO;
//import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
//import de.thwildau.f4f.studycompanion.datamodel.SensorLoggingConfigData;
//
//public class GarminSensorConfigurator {
//    private static final String LOG_TAG = "GarminSensorConfig";
//    private static int configurationCounter = 0;
//    private static boolean fatalError = false;
//
//    private static void checkFinishedConfigureLogging() {
//        configurationCounter--;
//        if(configurationCounter == 0) {
//            Log.d(LOG_TAG, "Garmin Sensor Logging configuration finished.");
//        }
//    }
//
//    /**
//     *
//     * @param device
//     * @return false, if a heavy error occurred which forced the process to be interrupted, true otherwise
//     */
//    public static boolean configureLogging(Device device) {
//        if(configurationCounter > 0) {
//            Log.d(LOG_TAG, "Attempted to start Garmin Sensor Logging configuration, but configuration is already running.");
//            return false;
//        }
//
//        List<SensorLoggingConfigData> logConfigList = SchemaProvider.getDeviceConfig().getGarminLoggingConfig();
//
//        configurationCounter = logConfigList.size();
//
//        Log.d(LOG_TAG, "Garmin Sensor Logging configuration started. " + configurationCounter + " logging parameters will be configured.");
//
//        for(SensorLoggingConfigData logConfig : logConfigList) {
//            LoggingType logSource;
//            try {
//                logSource = LoggingType.valueOf(logConfig.getSource());
//            } catch(IllegalArgumentException e) {
//                Log.w(LOG_TAG, "Error parsing Garmin Sensor Logging Config: '" + logConfig.getSource() + "' ist not a valid DataSource.");
//                checkFinishedConfigureLogging();
//                continue;
//            }
//
//            Integer samplingInterval;
//            if(LoggingType.CREATOR.getNoIntervalSources().contains(logSource)) {
//                samplingInterval = null;
//            } else {
//                if(!logConfig.hasInterval()) {
//                    Log.w(LOG_TAG, "Error parsing Garmin Sensor Logging Config: No interval specified for '" + logConfig.getSource() + "'. ");
//                    checkFinishedConfigureLogging();
//                    continue;
//                } else {
//                    samplingInterval = logConfig.getInterval();
//                }
//            }
//
//            boolean enabled = logConfig.isEnabled();
//            fatalError = false;
//
//            // Apply logging setting to Garmin Sensor according to Garmin docs (DataLogging.html):
//            try
//            {
//                Futures.getChecked(device.getLoggingStatus (logSource, loggingStatus ->
//                {
//                    int targetSamplingInterval = samplingInterval == null ? logSource.getDefaultInterval() : samplingInterval;
//
//                    if(samplingInterval != null && !logSource.isValidInterval(logSource, targetSamplingInterval)) {
//                        Integer defaultInterval = logSource.getDefaultInterval();
//                        BackendIO.serverLog(Log.WARN, LOG_TAG, "Error reading Garmin Sensor Logging Config: Interval for '" + logSource + "' is invalid. Interval: " + targetSamplingInterval+", set to defaultInterval: "+defaultInterval);
//                        targetSamplingInterval = defaultInterval;
//                    }
//
//                    if (!(loggingStatus instanceof Success)) {
//                        boolean isFailure = loggingStatus instanceof Failure;
//                        boolean isNull = loggingStatus == null;
//
//                        BackendIO.serverLog(Log.WARN, LOG_TAG, "Error reading Garmin Sensor Logging Config: loggingStatus for '" + logSource + "' is unavailable. isNull: " + isNull + ", isFailure: " + isFailure);
//                        checkFinishedConfigureLogging();
//                        return null;
//                    }
//
//                    LoggingConfiguration processedStatus = ((Success<LoggingConfiguration>) loggingStatus).component1();
//
//
//                    boolean currentlyEnabled = processedStatus.getEnabled();
//                    Integer currentInterval = processedStatus.getInterval();
//
//
//                    boolean needsUpdate = currentlyEnabled != enabled || (enabled && !Objects.equals(currentInterval, targetSamplingInterval));
//
//                    if(needsUpdate) {
//                        BackendIO.serverLog(Log.DEBUG, LOG_TAG, "Garmin logging Config '"+logSource+"' needs to be updated.");
//                        try {
//
//                            Futures.getChecked( device.setLoggingWithInterval(logSource, enabled, targetSamplingInterval, loggingChangedStatus ->
//                            {
//
//                                if (!(loggingChangedStatus instanceof Success)) {
//                                    boolean isFailure = loggingChangedStatus instanceof Failure;
//                                    boolean isNull = loggingChangedStatus == null;
//
//                                    BackendIO.serverLog(Log.WARN, LOG_TAG, "Error applying Garmin Sensor Logging Config: loggingChangedStatus for '" + logSource + "' is unavailable. isNull: " + isNull + ", isFailure: " + isFailure);
//                                } else {
//                                    BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Configured Logging setting '" + logConfig.getSource() + "': Interval = "+samplingInterval + ", Enabled = " + enabled);
//                                }
//
//                                checkFinishedConfigureLogging();
//                                return null;
//                            }), Exception.class);
//
//
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Error applying Garmin Sensor Logging Config: Could not apply setting '" +logSource + "' due to communication problem: " + e);
//                            BackendIO.serverLog(Log.INFO, LOG_TAG, "Garmin Sensor Logging Config process interrupted..");
//                            fatalError = true;
//                            checkFinishedConfigureLogging();
//                        }
//
//                    } else {
//                        checkFinishedConfigureLogging();
//                    }
//
//                    return null;
//                }), Exception.class);
//            }
//            catch (Exception e)
//            {
//                e.printStackTrace();
//                Log.e(LOG_TAG, "Error applying Garmin Sensor Logging Config: Could not apply setting '" + logConfig.getSource() + "' due to communication problem: " + e);
//                Log.i(LOG_TAG, "Garmin Sensor Logging Config process interrupted..");
//                fatalError = true;
//            }
//
//            if(fatalError) {
//                configurationCounter = 0;
//                return false;
//            }
//
//        }
//        return true;
//    }
//
// // Keep this for reference (TODO: Remove when successfully tested)
//// -----------------------------------------
////    public static boolean configureLoggingOld(Device device) {
////        if(configurationCounter > 0) {
////            Log.d(LOG_TAG, "Attempted to start Garmin Sensor Logging configuration, but configuration is already running.");
////            return false;
////        }
////
////        List<SensorLoggingConfigData> logConfigList = SchemaProvider.getDeviceConfig().getGarminLoggingConfig();
////
////        configurationCounter = logConfigList.size();
////
////        Log.d(LOG_TAG, "Garmin Sensor Logging configuration started. " + configurationCounter + " logging parameters will be configured.");
////
////        for(SensorLoggingConfigData logConfig : logConfigList) {
////            DataSource logSource;
////            try {
////                logSource = DataSource.valueOf(logConfig.getSource());
////            } catch(IllegalArgumentException e) {
////                Log.w(LOG_TAG, "Error parsing Garmin Sensor Logging Config: '" + logConfig.getSource() + "' ist not a valid DataSource.");
////                checkFinishedConfigureLogging();
////                continue;
////            }
////
////            int samplingInterval;
////            if(DataSource.NO_INTERVAL_SOURCES.contains(logSource)) {
////                samplingInterval = logSource.defaultInterval();
////            } else {
////                if(!logConfig.hasInterval()) {
////                    Log.w(LOG_TAG, "Error parsing Garmin Sensor Logging Config: No interval specified for '" + logConfig.getSource() + "'. ");
////                    checkFinishedConfigureLogging();
////                    continue;
////                } else {
////                    samplingInterval = logConfig.getInterval();
////                }
////            }
////
////            boolean enabled = logConfig.isEnabled();
////            fatalError = false;
////
////            // Apply logging setting to Garmin Sensor according to Garmin docs (DataLogging.html):
////            try
////            {
////                Futures.getChecked(DeviceManager.getDeviceManager().getLoggingState(device.address(), logSource, loggingStatus ->
////                {
////                    if(loggingStatus == null || loggingStatus.getState() == null) {
////                        BackendIO.serverLog(Log.WARN, LOG_TAG, "Error reading Garmin Sensor Logging Config: state for '" + logSource + "' is null.");
////                        checkFinishedConfigureLogging();
////                        return;
////                    }
////
////                    LoggingStatus.State state = loggingStatus.getState();
////
////                    boolean currentlyEnabled;
////                    Integer currentInterval;
////                    if(state == LoggingStatus.State.LOGGING_ON) {
////                        currentlyEnabled = true;
////                    } else if(state == LoggingStatus.State.LOGGING_OFF) {
////                        currentlyEnabled = false;
////                    } else {
////                        BackendIO.serverLog(Log.WARN, LOG_TAG, "Invalid state when reading Garmin Sensor Logging Config '" + logSource + "':  " + state);
////                        if(state != LoggingStatus.State.LOGGING_TYPE_UNAVAILABLE) {
////                            BackendIO.serverLog(Log.ERROR, LOG_TAG, "The last invalid config state error cannot be ignored. Garmin Sensor Logging Config process will be interrupted." );
////                            fatalError = true;
////                        }
////                        checkFinishedConfigureLogging();
////                        return;
////                    }
////
////                    currentInterval = loggingStatus.getInterval();
////
////                    boolean needsUpdate = currentlyEnabled != enabled || (enabled && !Objects.equals(currentInterval, samplingInterval));
////
////
////                    if(needsUpdate) {
////                        BackendIO.serverLog(Log.DEBUG, LOG_TAG, "Garmin logging Config '"+logSource+"' needs to be updated.");
////                        try {
////                            Futures.getChecked(DeviceManager.getDeviceManager().setLoggingStateWithInterval(device.address(), logSource, enabled, samplingInterval, loggingStatusPost ->
////                            {
////
////                                if(loggingStatusPost != null && loggingStatusPost.getState() != LoggingStatus.State.LOGGING_ON && loggingStatusPost.getState() != LoggingStatus.State.LOGGING_OFF)
////                                {
////                                    LoggingStatus.State postState = loggingStatusPost.getState();
////                                    BackendIO.serverLog(Log.WARN, LOG_TAG, "Error applying Garmin Sensor Logging Config: Could not apply setting '" + logConfig.getSource() + "'. Reason: " + state );
////                                    if(postState != LoggingStatus.State.LOGGING_TYPE_UNAVAILABLE) {
////                                        BackendIO.serverLog(Log.ERROR, LOG_TAG, "The last config application error cannot be ignored. Garmin Sensor Logging Config process will be interrupted." );
////                                        fatalError = true;
////                                    }
////                                } else {
////                                    BackendIO.serverLog(Log.DEBUG, LOG_TAG,"Configured Logging setting '" + logConfig.getSource() + "': Interval = "+samplingInterval + ", Enabled = " + enabled);
////                                }
////
////                                checkFinishedConfigureLogging();
////                            }), Exception.class);
////                        } catch (Exception e) {
////                            e.printStackTrace();
////                            BackendIO.serverLog(Log.ERROR, LOG_TAG, "Error applying Garmin Sensor Logging Config: Could not apply setting '" +logSource + "' due to communication problem: " + e);
////                            BackendIO.serverLog(Log.INFO, LOG_TAG, "Garmin Sensor Logging Config process interrupted..");
////                            fatalError = true;
////                            checkFinishedConfigureLogging();
////                        }
////
////                    } else {
////                        checkFinishedConfigureLogging();
////                    }
////                }), Exception.class);
////            }
////            catch (Exception e)
////            {
////                e.printStackTrace();
////                Log.e(LOG_TAG, "Error applying Garmin Sensor Logging Config: Could not apply setting '" + logConfig.getSource() + "' due to communication problem: " + e);
////                Log.i(LOG_TAG, "Garmin Sensor Logging Config process interrupted..");
////                fatalError = true;
////            }
////
////            if(fatalError) {
////                configurationCounter = 0;
////                return false;
////            }
////
////        }
////        return true;
////    }
//}
