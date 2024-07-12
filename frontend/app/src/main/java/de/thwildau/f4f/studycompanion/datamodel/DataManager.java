package de.thwildau.f4f.studycompanion.datamodel;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.SyncableData;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

import static de.thwildau.f4f.studycompanion.StudyCompanion.getAppContext;

public class DataManager {

    private static final String LOG_TAG = "DataManager";

    private static Utils.ObservableValue<Date> lastSyncDate = null;
    private static Utils.ObservableValue<Boolean> modifiedSinceLastSync = null;

    public static class NoPermissionException extends Exception {
        public NoPermissionException() {
            super("Currently only Participants are allowed to access local database.");
        }
    }

    public interface OnSynchronizationProcessStateChangeListener {
        void onStartedUploading(int numberOfDatasetsToUpload);

        void onStartedDownloading();

        void onSynchronizationProgress(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded);

        void onSynchronizationCancelled();

        void onSynchronizationCompleted(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded);

        void onSynchronizationError();
    }

    private enum SyncProcessState {
        STARTED,
        SYNC_PROC_ID_RECEIVED,
        DOWNLOADED_USER_DATA,
        STORED_USER_DATA,
        DOWNLOADED_LAB_DATA,
        STORED_LAB_DATA,
        UPLOADED_USER_DATA,
        STORED_USER_DATA_REMOTE_KEYS,
        UPLOADED_SENSOR_DATA,
        STORED_SENSOR_DATA_REMOTE_KEYS,
        SYNC_CONFIRMED,
        SYNC_PROCESS_FINISHED,
        UNEXPECTED_ERROR,
        CANCELLED;
    }

    // Synchronization Process State Machine state fields:
    private static SyncProcessState syncProcessState;
    private static int numDatasetsToUpload;
    private static int numUploadedDatasets;
    private static int numDownloadedDatasets;
    private static boolean downloadAllData;
    private static List<SyncableData> syncedDatasets;
    private static String syncProcId;
    private static OnSynchronizationProcessStateChangeListener synchronizationStatusChangedListener = null;
    private static StateMachineThread stateMachineThread;
    private static Realm syncRealm;
    // End: Synchronization State Machine state fields

    private static Object syncLock = new Object();

    private static class StateMachineThread extends Thread {
        boolean stopped;
        public SyncProcessState nextState = null;
        private JSONArray downloadedData = null;
        private List<String> remoteIds = null;
        List<SyncableData> localDatasets = null;

        @Override
        public void run() {
            stopped = false;
            nextState = null;
            syncRealm = Realm.getDefaultInstance();

            while (!stopped) {
                try {


                    switch (syncProcessState) {
                        case STARTED:
                            NotificationOrganizer.showSyncNotification(NotificationOrganizer.SyncType.ServerSync);
                            Log.d(LOG_TAG, "Started Synchronization process.");
                            Log.d(LOG_TAG, "Requesting Sync Process ID.");
                            BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.SYNC, new BackendIO.RemoteRequestCompletedCallback() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    if (stopped) {
                                        return;
                                    }

                                    try {
                                        syncProcId = response.getString("sync_id");
                                        changeState(SyncProcessState.SYNC_PROC_ID_RECEIVED);
                                        Log.d(LOG_TAG, "Received Sync Process ID: " + syncProcId);
                                    } catch (JSONException e) {
                                        handleInvalidResponseError("Could not acquire sync process ID.");
                                    }
                                }

                                @Override
                                public void onError(int errorStatusCode, String errorMessage) {
                                    if (stopped) {
                                        return;
                                    }


                                    handleCommunicationError(errorStatusCode, errorMessage);
                                }
                            });

                            break;

                        case SYNC_PROC_ID_RECEIVED:
                            if (synchronizationStatusChangedListener != null) {
                                synchronizationStatusChangedListener.onStartedDownloading();
                            }

                            // no break!!
                        case STORED_USER_DATA: {
                            JSONObject request = new JSONObject();
                            DataType dataType = (syncProcessState == SyncProcessState.STORED_USER_DATA ? DataType.LabData : DataType.UserData);
                            try {
                                request.put("datatype", dataType.toString());
                                if (downloadAllData) {
                                    request.put("all", "true");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace(); // shouldn't happen
                            }

                            Log.d(LOG_TAG, "Starting download of " + (downloadAllData ? "ALL " : "unsynched ") + dataType.toString() + ".");

                            BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.SYNC, request, syncProcId, new BackendIO.RemoteRequestCompletedCallback() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    if (stopped) {
                                        return;
                                    }

                                    try {
                                        downloadedData = response.getJSONArray("data");
                                        numDownloadedDatasets += downloadedData.length();
                                        Log.d(LOG_TAG, "Received " + downloadedData.length() + " data sets.");
                                        if (synchronizationStatusChangedListener != null) {
                                            synchronizationStatusChangedListener.onSynchronizationProgress(numUploadedDatasets, numDownloadedDatasets);
                                        }
                                        changeState(syncProcessState == SyncProcessState.SYNC_PROC_ID_RECEIVED ? SyncProcessState.DOWNLOADED_USER_DATA : SyncProcessState.DOWNLOADED_LAB_DATA);
                                    } catch (JSONException e) {
                                        handleInvalidResponseError("Could not acquire " + dataType.toString());
                                    }
                                }

                                @Override
                                public void onError(int errorStatusCode, String errorMessage) {
                                    if (stopped) {
                                        return;
                                    }


                                    handleCommunicationError(errorStatusCode, errorMessage);
                                }
                            });
                        }

                        break;

                        case DOWNLOADED_USER_DATA:
                            try {
                                Log.d(LOG_TAG, "Saving downloaded User Data in local storage.");
                                storeDownloadedData(DataType.UserData, downloadedData);
                                changeState(SyncProcessState.STORED_USER_DATA);
                            } catch (JSONException e) {
                                handleInvalidResponseError("Could not interpret User Data.");
                            }
                            break;

                        case DOWNLOADED_LAB_DATA:
                            try {
                                Log.d(LOG_TAG, "Saving downloaded Lab Data in local storage.");
                                storeDownloadedData(DataType.LabData, downloadedData);
                                changeState(SyncProcessState.STORED_LAB_DATA);
                            } catch (JSONException e) {
                                handleInvalidResponseError("Could not interpret Lab Data.");
                            }
                            break;

                        case STORED_LAB_DATA:
                            determineNumberOfDatasetsToUpload();
                            if (synchronizationStatusChangedListener != null) {
                                synchronizationStatusChangedListener.onStartedUploading(numDatasetsToUpload);
                            }
                            // no break!
                        case STORED_USER_DATA_REMOTE_KEYS: {

                            JSONArray dataToUpload = new JSONArray();
                            DataType dataType = syncProcessState == SyncProcessState.STORED_LAB_DATA ? DataType.UserData : DataType.SensorData;
                            localDatasets = new ArrayList<>();

                            Log.d(LOG_TAG, "Gathering " + (downloadAllData ? "ALL" : "unsynched") + " local " + dataType.toString() + ".");

                            getLocalDataToUpload(dataType, dataToUpload, localDatasets);

                            JSONObject requestData = new JSONObject();
                            try {
                                requestData.put("datatype", dataType.toString());
                                requestData.put("data", dataToUpload);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            Log.d(LOG_TAG, "Starting upload of " + dataType.toString() + " (" + localDatasets.size() + " data sets).");

                            BackendIO.sendRemoteDatasetAsync(requestData, BackendIO.RemoteDatasetType.SYNC, syncProcId, new BackendIO.RemoteRequestCompletedCallback() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    if (stopped) {
                                        return;
                                    }

                                    try {
                                        // TODO: Implement segmented upload of SensorData, for returning Upload Progress feedbacks and  limit the size of single transmission (to make process cancelable)
                                                // Note: Too risky to touch this before the beginning of the study. We'll optimize this for the second study.
                                        JSONArray remoteIdsJson = response.getJSONArray("identifiers");
                                        int numRemoteIds = remoteIdsJson.length();
                                        if (numRemoteIds != localDatasets.size()) {
                                            handleInvalidResponseError("Received unexpected number of remote identifiers for " + dataType.name());
                                            return;
                                        }

                                        remoteIds = new ArrayList<>(numRemoteIds);

                                        for (int i = 0; i < numRemoteIds; i++) {
                                            remoteIds.add(remoteIdsJson.getString(i));
                                        }

                                        Log.d(LOG_TAG, "Upload finished. Store " + numRemoteIds + " remote identifiers.");
                                        increaseUploadProgress(numRemoteIds);

                                        if (synchronizationStatusChangedListener != null) {
                                            synchronizationStatusChangedListener.onSynchronizationProgress(numUploadedDatasets, numDownloadedDatasets);
                                        }

                                        changeState(syncProcessState == SyncProcessState.STORED_LAB_DATA ? SyncProcessState.UPLOADED_USER_DATA : SyncProcessState.UPLOADED_SENSOR_DATA);

                                    } catch (JSONException e) {
                                        handleInvalidResponseError("Could not acquire " + dataType.name() + " remote identifiers.");
                                    }
                                }

                                @Override
                                public void onError(int errorStatusCode, String errorMessage) {
                                    if (stopped) {
                                        return;
                                    }
                                    handleCommunicationError(errorStatusCode, errorMessage);
                                }
                            });
                        }
                        break;

                        case UPLOADED_USER_DATA:
                            Log.d(LOG_TAG, "Storing " + remoteIds.size() + " User Data remote keys.");
                            storeRemoteIds(localDatasets, remoteIds);
                            changeState(SyncProcessState.STORED_USER_DATA_REMOTE_KEYS);
                            break;

                        case UPLOADED_SENSOR_DATA:
                            Log.d(LOG_TAG, "Storing " + remoteIds.size() + " Sensor Data remote keys.");
                            storeRemoteIds(localDatasets, remoteIds);
                            changeState(SyncProcessState.STORED_SENSOR_DATA_REMOTE_KEYS);
                            break;


                        case STORED_SENSOR_DATA_REMOTE_KEYS:
                            Log.d(LOG_TAG, "Confirming sync process.");
                            BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.SYNC, syncProcId + "/finish", new BackendIO.RemoteRequestCompletedCallback() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    if (stopped) {
                                        return;
                                    }

                                    try {
                                        boolean success = response.getBoolean("success");
                                        if (!success) {
                                            throw new IllegalStateException();
                                        }

                                        Log.d(LOG_TAG, "Sync process confirmed by server.");

                                        changeState(SyncProcessState.SYNC_CONFIRMED);

                                    } catch (IllegalStateException | JSONException e) {
                                        handleInvalidResponseError("Server did not confirm sync process termination.");
                                    }
                                }

                                @Override
                                public void onError(int errorStatusCode, String errorMessage) {
                                    if (stopped) {
                                        return;
                                    }

                                    handleCommunicationError(errorStatusCode, errorMessage);
                                }
                            });
                            break;

                        // Sync process termination states:

                        case SYNC_CONFIRMED:
                            Log.d(LOG_TAG, "Mark local data as synchronized.");
                            markDataAsSynched();
                            changeState(SyncProcessState.SYNC_PROCESS_FINISHED);
                            break;

                        case UNEXPECTED_ERROR:
                            Log.d(LOG_TAG, "Sync process terminated with an error.");
                            if (synchronizationStatusChangedListener != null) {
                                synchronizationStatusChangedListener.onSynchronizationError();
                            }
                            stopped = true;
                            break;
                        case CANCELLED:
                            Log.d(LOG_TAG, "Sync process cancelled.");
                            if (synchronizationStatusChangedListener != null) {
                                synchronizationStatusChangedListener.onSynchronizationCancelled();
                            }
                            stopped = true;
                            break;
                        case SYNC_PROCESS_FINISHED:
                            Log.d(LOG_TAG, "Sync process finished successfully.");
                            if (synchronizationStatusChangedListener != null) {
                                synchronizationStatusChangedListener.onSynchronizationCompleted(numUploadedDatasets, numDownloadedDatasets);
                            }
                            setLocalSynchronizationState(true);
                            stopped = true;
                    } // end switch
                } catch (Exception e) {
                    handleError("Unexpected error during synchronization process: " + e.toString());
                    e.printStackTrace();
                }

                if (stopped) {
                    initState();
                    syncRealm.close();
                    NotificationOrganizer.hideSyncNotification(NotificationOrganizer.SyncType.ServerSync);
                    break;
                }

                try {
                    synchronized (syncLock) {
                        if (nextState == null) {
                            // wait for next interrupt / state change
                            syncLock.wait();
                        }

                        if (nextState != null) {
                            syncProcessState = nextState;
                            nextState = null;
                        }
                    }
                } catch (InterruptedException e) {
                    // shouldn't happen
                }
            } // end state loop
        } // end run method
    }

    public static boolean isSyncInProgress() {
        return (
                syncProcessState != SyncProcessState.SYNC_PROCESS_FINISHED &&
                        syncProcessState != SyncProcessState.CANCELLED &&
                        syncProcessState != null &&
                        syncProcessState != SyncProcessState.UNEXPECTED_ERROR);
    }

    private static RealmQuery<SyncableData> baseQuery(Realm realm) {
        return realm.where(SyncableData.class).equalTo("userId", BackendIO.getCurrentUser().id);
    }

    private static void getLocalDataToUpload(DataType dataType, JSONArray jsonData, List<SyncableData> localDatasetList) {
        Realm realm = syncRealm;
        RealmResults<SyncableData> localDatasets = baseQuery(realm)
                .equalTo("dataTypeStr", dataType.toString())
                .isNull("lastSyncId")
                .limit(1000) // Limit to 1000 items to avoid OutOfMemoryErrors. If there are more items, we transfer chunks of data in every sync process.
                .findAll();

        int numDatasets = localDatasets.size();
        BackendIO.serverLog(Log.DEBUG, LOG_TAG, "Number of " + dataType + " datasets to upload: "+ numDatasets);

        int count = 1;

        for (SyncableData localDataset : localDatasets) {
            Log.v(LOG_TAG, "Gathering local " + dataType + " dataset: " + count + " of " + numDatasets);
            count++;

            JSONObject remoteJsonObject = localDataset.toRemoteJsonObject();
            syncedDatasets.add(localDataset);
            if (remoteJsonObject == null) {
                // data set is locally marked for deletion.
                // It will be deleted , after sync process has finished, in markDataAsSynched()
                continue;
            }
            jsonData.put(remoteJsonObject);
            localDatasetList.add(localDataset);
        }

    }

    private static void storeRemoteIds(List<SyncableData> localDatasets, List<String> remoteIds) {

        Realm realm = syncRealm;

        realm.executeTransaction(r -> {
            if (localDatasets.size() != remoteIds.size()) {
                throw new RuntimeException("Sizes of local dataset list and remote ID list differ.");
            }

            for (int i = 0; i < localDatasets.size(); i++) {
                localDatasets.get(i).setRemoteId(remoteIds.get(i));
            }
        });
    }

    private static void determineNumberOfDatasetsToUpload() {
        // All datasets, which do not have a last sync process ID attached are needed to be uploaded
        Realm realm = syncRealm;
        numDatasetsToUpload = (int) baseQuery(realm).
                isNull("lastSyncId").
                count();
    }

    private static void storeDownloadedData(DataType dataType, JSONArray data) throws JSONException {
        Realm realm = syncRealm;

        for (int i = 0; i < data.length(); i++) {
            JSONObject remoteDataset = data.getJSONObject(i);
            String remoteId = remoteDataset.getString("id");

            SyncableData localDataset = baseQuery(realm).equalTo("remoteId", remoteId).findFirst();

            boolean newDataset = false;

            if (localDataset == null) {
                // Dataset does not yet exist locally

                localDataset = new SyncableData(realm);
                newDataset = true;
            }

            realm.beginTransaction();

            // Update local dataset with new remote information
            localDataset.fromJsonObject(remoteDataset, dataType);

            // Mark dataset as synced
            localDataset.setLastSyncId(syncProcId);

            if (newDataset) {
                // insert, if it's a locally new dataset
                realm.insert(localDataset);
            }

            realm.commitTransaction();

            // store dataset in list of synced datasets
            syncedDatasets.add(localDataset);
        }
    }

    private static void markDataAsSynched() {
        Realm realm = syncRealm;
        realm.executeTransaction(r -> {
            List<SyncableData> deletedLocalDatasets = new ArrayList<>();
            for (SyncableData syncedDataset : syncedDatasets) {
                if (syncedDataset.isMarkedForDeletion()) {
                    deletedLocalDatasets.add(syncedDataset);
                } else {
                    syncedDataset.setLastSyncId(syncProcId);
                }
            }

            for (SyncableData deletedLocalDataset : deletedLocalDatasets) {
                deletedLocalDataset.deleteFromRealm();
            }
        });
    }


    private static void increaseUploadProgress(int uploadIncrease) {
        numUploadedDatasets += uploadIncrease;
    }

    private static void initState() {
        downloadAllData = false;
        numDatasetsToUpload = 0;
        numUploadedDatasets = 0;
        numDownloadedDatasets = 0;
        synchronizationStatusChangedListener = null;
        syncProcId = null;
        syncedDatasets = new ArrayList<>();
        stateMachineThread = new StateMachineThread();
    }

    public static void startSynchronization(OnSynchronizationProcessStateChangeListener onSynchronizationProcessStateChangeListener) throws NoPermissionException {
        if (isSyncInProgress()) {
            if (onSynchronizationProcessStateChangeListener != null) {
                // Respond with error, because sync is already running
                BackendIO.serverLog(Log.WARN, LOG_TAG,"Server Sync request denied because another sync process is already running.");
                onSynchronizationProcessStateChangeListener.onSynchronizationError();
            }
            return;
        }

        initState();

        synchronizationStatusChangedListener = onSynchronizationProcessStateChangeListener;

        checkPermission();

        syncProcessState = SyncProcessState.STARTED;

        String prefName = StudyCompanion.getAppContext().getString(R.string.lastServerSyncTime);
        if (StudyCompanion.getUserPreferences().getLong(prefName, 0) == 0) {
            // No date of former complete data sync registered.
            // User either has never uploaded anything or is freshly logged in. In this case, download ALL existing data (and not only unsynched data) to populate local storage.
            downloadAllData = true;
        }

        stateMachineThread.start();
    }

    public static void checkPermission() throws NoPermissionException {
        User currentUser = BackendIO.getCurrentUser();
        if (currentUser == null || currentUser.role != Role.Participant) {
            throw new NoPermissionException();
        }
    }

    private static void changeState(SyncProcessState newSyncProcessState) {
        synchronized (syncLock) {
            stateMachineThread.nextState = newSyncProcessState;
            syncLock.notify();
        }
    }


    private static void handleInvalidResponseError(String errorMessage) {
        handleError(getAppContext().getString(R.string.datamanager_error_invalid_response, errorMessage));
    }

    private static void handleCommunicationError(int errorStatusCode, String errorMessage) {
        handleError(getAppContext().getString(R.string.datamanager_error_communication, errorStatusCode, errorMessage));
    }

    private static void handleError(String msg) {
        BackendIO.serverLog(Log.ERROR, LOG_TAG,msg + ", Sync State: " + syncProcessState);

        changeState(SyncProcessState.UNEXPECTED_ERROR);
    }


    public static void cancelSynchronization() {
        changeState(SyncProcessState.CANCELLED);
    }

    public static void updateOrInsertData(DataType dataType, JSONObject data) throws NoPermissionException {
        final Long localId;
        final SyncableData localDataset;

        checkPermission();

        Realm realm = Realm.getDefaultInstance();

        try {
            JSONObject dataCopy = new JSONObject(data.toString());

            if (dataCopy.has("id")) {
                localId = Long.parseLong(dataCopy.getString("id"));
                localDataset = baseQuery(realm).equalTo("localId", localId).findFirst();
                if (localDataset == null) {
                    // Specifiing an "id" field implicates modifying a dataset, but there was no dataset found correpsonding to the given id.
                    throw new IllegalStateException("No local dataset found for specified ID.");
                }
                dataCopy.remove("id");
            } else {
                localId = null;
                localDataset = new SyncableData(realm); // Creation date is set implicitly
            }


            realm.executeTransaction(r -> {
                try {
                    // Update dataset in local database
                    localDataset.fromJsonObject(dataCopy, dataType);

                    // remove Sync ID and update modification date
                    localDataset.invalidate();

                    if (localId == null) {
                        // if it's a new data set, insert it in local database
                        final SyncableData localDatasetFinal = localDataset;
                        r.insert(localDatasetFinal);
                    }

                } catch (JSONException | NumberFormatException e) {
                    e.printStackTrace();
                }
            });


            // Mark local database as unsynched due to modification
            setLocalSynchronizationState(false);

        } catch (JSONException | NumberFormatException e) {
            e.printStackTrace();
        }
        realm.close();
    }

    public static List<JSONObject> getAllDatasets(DataType dataType) throws NoPermissionException {
        checkPermission();
        if (dataType == DataType.SensorData) {
            throw new RuntimeException("Read access for Sensor Data is not supported.");
        }

        Realm realm = Realm.getDefaultInstance();

        RealmResults<SyncableData> localDatasets = baseQuery(realm).equalTo("dataTypeStr", dataType.toString()).equalTo("markedForDeletion", false).findAll();
        List<JSONObject> res = new ArrayList<>(localDatasets.size());

        try {
            for (SyncableData localDataset : localDatasets) {
                JSONObject datasetJson = localDataset.toRemoteJsonObject();
                datasetJson.put("id", localDataset.getLocalId().toString());
                res.add(datasetJson); // return dataset with local ID instead of server ID
            }
        } catch (JSONException e) {

        }

        realm.close();

        return res;
    }

    public static JSONObject getUserDataForEffectiveDay(Date effectiveDay) throws NoPermissionException {

        List<JSONObject> userData = getAllDatasets(DataType.UserData);

        long minDateMs = Utils.setTimeToZero(effectiveDay).getTime();
        long maxDateMs = Utils.setTimeToNextMidnight(effectiveDay).getTime();

        if (userData.isEmpty()) {
            return null;
        }

        for (JSONObject dataset : userData) {
            try {
                long dataDayMs = Utils.getServerTimeFormat().parse(dataset.optString("effective_day")).getTime();
                if (dataDayMs >= minDateMs && dataDayMs < maxDateMs) {
                    return dataset;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static boolean deleteDataset(String localId) throws NoPermissionException {
        checkPermission();
        Realm realm = Realm.getDefaultInstance();
        SyncableData localDataset = baseQuery(realm).equalTo("id", localId).findFirst();
        if (localDataset == null) {
            return false;
        }

        realm.executeTransaction(r -> {
            localDataset.setMarkedForDeletion(true);
        });

        realm.close();

        // Mark local database as unsynched due to modification
        setLocalSynchronizationState(false);

        return true;
    }

    private static void initObservables() {
        SharedPreferences sp = StudyCompanion.getUserPreferences();

        String prefName;
        if (lastSyncDate == null) {
            lastSyncDate = new Utils.ObservableValue<>(null);

            if (sp != null) {
                prefName = StudyCompanion.getAppContext().getString(R.string.lastServerSyncTime);
                long lastSyncTimestamp = sp.getLong(prefName, 0);
                if (lastSyncTimestamp != 0) {
                    lastSyncDate.setValue(new Date(lastSyncTimestamp));
                }
            }
        }

        if (modifiedSinceLastSync == null) {
            boolean dataModified = true;
            if (sp != null) {
                prefName = StudyCompanion.getAppContext().getString(R.string.dataModified);
                dataModified = sp.getBoolean(prefName, true);
            }

            modifiedSinceLastSync = new Utils.ObservableValue<>(dataModified);
        }
    }

    private static void setLocalSynchronizationState(boolean isSynchronized) {
        getObservableModifiedSinceLastSync().setValue(!isSynchronized);
        String prefName = StudyCompanion.getAppContext().getString(R.string.dataModified);
        StudyCompanion.getUserPreferences().edit().putBoolean(prefName, !isSynchronized).apply();
        if (isSynchronized) {
            updateLastSyncDate();
        }
    }

    private static void updateLastSyncDate() {
        Date now = new Date();
        String prefName = StudyCompanion.getAppContext().getString(R.string.lastServerSyncTime);
        StudyCompanion.getUserPreferences().edit().putLong(prefName, now.getTime()).apply();
        getObservableLastSyncDate().setValue(now);
    }

    public static void wipeAllLocalDataForUser(User user) {

        Realm realm = Realm.getDefaultInstance();
        RealmResults<SyncableData> results = realm.where(SyncableData.class).equalTo("userId", user.id).findAll();
        realm.beginTransaction();
        results.deleteAllFromRealm();
        realm.commitTransaction();
        realm.close();

        // Reset last sync date
        getObservableLastSyncDate().setValue(null);
        String prefName = StudyCompanion.getAppContext().getString(R.string.lastServerSyncTime);
        StudyCompanion.getUserPreferences(user).edit().remove(prefName).apply();
        prefName = StudyCompanion.getAppContext().getString(R.string.dataModified);
        StudyCompanion.getUserPreferences(user).edit().remove(prefName).apply();
    }


    public static void wipeAllSynchronizedSensorData() throws NoPermissionException {
        checkPermission();
        Realm realm = Realm.getDefaultInstance();
        RealmResults<SyncableData> datasets = baseQuery(realm)
                .equalTo("dataTypeStr", DataType.SensorData.toString())
                .isNotNull("lastSyncId")
                .findAll();

        realm.executeTransaction(r -> {
            datasets.deleteAllFromRealm();
        });

        realm.close();
    }

    public static Utils.ObservableValue<Date> getObservableLastSyncDate() {
        initObservables();
        return lastSyncDate;
    }

    public static Utils.ObservableValue<Boolean> getObservableModifiedSinceLastSync() {
        initObservables();
        return modifiedSinceLastSync;
    }
}
