package de.thwildau.f4f.studycompanion.background;

import android.app.Notification;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.WorkerParameters;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.AppUpdater;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;

public class BackendSyncWorker extends WorkerBase {
    private static final String LOG_TAG = "BackendSyncWorker";
    public BackendSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private boolean error;

    @Override
    public void startWorkAsync() {

// This "wifi check" was replaced by network type constraint for WorkRequest,
// since it sometimes returned false even though connected through WiFi on test device.
//        if(!Utils.isConnectedToWifi()) {
//            Log.i(LOG_TAG, "Attempt to auto-sync with f4f backend server but not processed due to no WiFi connection.");
//            finishWork(Result.failure());
//            return;
//        }

        if(BackendIO.getCurrentUser() == null) {
            // User is not signed in
            BackendIO.serverLog(Log.INFO,LOG_TAG, "Attempt to auto-sync with f4f backend server but app is not connected to f4f backend.");
            finishWork(Result.success());
            return;
        }

        boolean isParticipant = BackendIO.getCurrentUser().role == Role.Participant;

        String logMsg = "Auto-sync with f4f backend server started. (Role: '"+BackendIO.getCurrentUser().role+"') Downloading schemas from server...";
        BackendIO.serverLog(Log.INFO, LOG_TAG,logMsg);

        error=false;

        // First download the schemas + config from Server:
        SchemaProvider.downloadSturcturesFromServer((withError, configUpdated) ->
        {
            Log.i(LOG_TAG, "Schemas received. Device config updated: " + configUpdated);
            if(configUpdated)
            {
                // This will only be the case when the device configuration was updated on server
                // since the last time schemas were downloaded.

                // In case sensor device configuration have been changed,
                // apply it to connected wearables.
                StudyCompanion.updateSensorConfig();

                // in case auto-sync configuration has been changed, re-initialize all workers
                WorkerBase.initAllWorkers();
            }

            // After Server Sync has completed:
            // Check for new APK Version and download in background, if available
            AppUpdater.getApkDownloadState().addObserver(new Utils.Observer<AppUpdater.ApkUpdateState>() {
                @Override
                public void onUpdate(Utils.ObservableValue<AppUpdater.ApkUpdateState> object, AppUpdater.ApkUpdateState state) {
                    if(state == AppUpdater.ApkUpdateState.FINISH_ERROR || state == AppUpdater.ApkUpdateState.FINISH_SUCCESS_OR_NO_DOWNLOAD_NEEDED) {
                        NotificationOrganizer.hideSyncNotification(NotificationOrganizer.SyncType.APKDownload);
                        if(state == AppUpdater.ApkUpdateState.FINISH_ERROR) {
                            error=true;
                        }

                        AppUpdater.getApkDownloadState().removeObserver(this);
                        BackendIO.serverLog(error ? Log.WARN : Log.INFO, LOG_TAG,"BackgroundSyncWorker finished" + (error ? " (with errors)." : "."));
                        finishWork(error ? Result.failure(): Result.success());
                    } else if(state == AppUpdater.ApkUpdateState.DOWNLOADING) {
                        Notification notification = NotificationOrganizer.createSyncNotificationWithProgress(NotificationOrganizer.SyncType.APKDownload, 0);
                        if(notification != null)
                            setForegroundAsync(new ForegroundInfo(NotificationOrganizer.SyncType.APKDownload.getNotificationId(), notification));
                    }
                }
            }, false);

            AppUpdater.getObservableApkDownloadProgress().addObserver(new Utils.Observer<Integer>() {
                @Override
                public void onUpdate(Utils.ObservableValue<Integer> object, Integer progress) {
                    Notification notification = NotificationOrganizer.createSyncNotificationWithProgress(NotificationOrganizer.SyncType.APKDownload, (progress != null) ? progress : 0);
                    if(notification != null)
                        setForegroundAsync(new ForegroundInfo(NotificationOrganizer.SyncType.APKDownload.getNotificationId(), notification));

                    if(progress != null && progress == 100) {
                          AppUpdater.getObservableApkDownloadProgress().removeObserver(this);
                    }
                }
            }, false);

            if(isParticipant)
                startServerSync(); // will implicitly call the ApkDownloadState handler when done
            else
                // only check for new App version if not a participant
                AppUpdater.tryDownloadNewApk();
        });


    }

    private void startServerSync() {
        try {
            // After new schemas + config received and processed, start data synchronization with server
            DataManager.startSynchronization(new DataManager.OnSynchronizationProcessStateChangeListener() {
                @Override
                public void onStartedUploading(int numberOfDatasetsToUpload) {
                    BackendIO.serverLog(Log.INFO, LOG_TAG,  "Started data synchronization with f4f backend.");

                }

                @Override
                public void onStartedDownloading() {
                    Log.i(LOG_TAG, "Downloading data from server.");
                }

                @Override
                public void onSynchronizationProgress(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded) {

                }

                @Override
                public void onSynchronizationCancelled() {
                    BackendIO.serverLog(Log.INFO, LOG_TAG,"Auto Server Sync cancelled." );
                    // error = true;

                    AppUpdater.tryDownloadNewApk(); // check for new APK version and finish Task
                }

                @Override
                public void onSynchronizationCompleted(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded) {
                    BackendIO.serverLog(Log.INFO, LOG_TAG,"Auto-sync with f4f backend server completed. Datasets Uploaded: " + numberOfDatasetsUploaded + ", Datasets downloaded: " + numberOfDatasetsDownloaded);
                    AppUpdater.tryDownloadNewApk(); // check for new APK version and finish Task
                }

                @Override
                public void onSynchronizationError() {
                    BackendIO.serverLog(Log.ERROR, LOG_TAG,"Auto Server Sync failed. (SynchronizationError), but Worker terminated successfully.");
                    // error = true;

                    AppUpdater.tryDownloadNewApk(); // check for new APK version and finish Task
                }
            });
        } catch(Exception e) {
            BackendIO.serverLog(Log.ERROR, LOG_TAG,"Attempt to auto-sync with f4f backend server, but initiation of sync process crashed with exception: " + e);

            // error = true;
            AppUpdater.tryDownloadNewApk();
        }
    }
}
