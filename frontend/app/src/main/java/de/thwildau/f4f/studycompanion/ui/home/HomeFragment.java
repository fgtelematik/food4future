package de.thwildau.f4f.studycompanion.ui.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.AppUpdater;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.background.WorkerBase;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import de.thwildau.f4f.studycompanion.ui.customform.CustomField;
import de.thwildau.f4f.studycompanion.ui.customform.FoodlistCustomField;
import de.thwildau.f4f.studycompanion.ui.login.LoginActivity;
import de.thwildau.f4f.studycompanion.ui.questions.QuestionsFragment;
import de.thwildau.f4f.studycompanion.ui.sensors.SensorManagementFragment;

public class HomeFragment extends Fragment {

    private static final String LOG_TAG = "HomeFragment";

    public static final String EXTRA_SCROLL_TO_COSINUSS_STATE_CONTAINER = "EXTRA_SCROLL_TO_COSINUSS_STATE_CONTAINER";
    private static final int REQUEST_INSTALL_PACKAGE_PERMISSION = 50;

    private enum ServerSyncState {
        Initial,
        Outdated,
        Syncing,
        Error,
        Completed
    }
    private Utils.Observer<Date> lastSyncTimeObserver = null;
    private StudyHomeStateUI studyStateUI;
//    private GarminHomeStateUI garminUI;
    private CosinussHomeStateUI cosinussUI; //TODO: Also outsource ServerSync UI's to improve code clarity


    private ServerSyncState serverSyncState = ServerSyncState.Initial;
    private View rootView;
    private FoodlistCustomField foodlistField = null;

    private static class UnobtainableUserDataException extends Exception { }

    private void generateUserSpecificView() {
        View anonymousView = rootView.findViewById(R.id.anonymousView);
        View genericView = rootView.findViewById(R.id.genericView);
        View participantView = rootView.findViewById(R.id.participantView);
        View nurseView = rootView.findViewById(R.id.nurseView);

        TextView textRole = rootView.findViewById(R.id.textRole);
        User user = BackendIO.getCurrentUser();

        if (user == null) {
            anonymousView.setVisibility(View.VISIBLE);
            genericView.setVisibility(View.GONE);
            participantView.setVisibility(View.GONE);
            nurseView.setVisibility(View.GONE);
        } else {
            textRole.setText(user.role.toString());
            nurseView.setVisibility(user.role == Role.Nurse ? View.VISIBLE : View.GONE);
            genericView.setVisibility(user.role == Role.Administrator ? View.VISIBLE : View.GONE);

            if (user.role == Role.Participant) {
                anonymousView.setVisibility(View.GONE);
                genericView.setVisibility(View.GONE);
                participantView.setVisibility(View.VISIBLE);
                updateServerSyncStatusUI();
                updateDailyQuestionUI();
                updateFoodlistStatusUI();
            } else {
                anonymousView.setVisibility(View.GONE);
                participantView.setVisibility(View.GONE);
            }
        }
    }

    private void updateServerSyncStatusUI() {
        final View syncViewContainer = rootView.findViewById(R.id.serverSyncViewContainer);
        final View goodSyncView = rootView.findViewById(R.id.serverSyncViewGood);
        final View badSyncView = rootView.findViewById(R.id.serverSyncViewBad);
        final View processingSyncView = rootView.findViewById(R.id.serverSyncViewProcessing);
        final View errorSyncView = rootView.findViewById(R.id.serverSyncViewError);
        final TextView goodSyncText = rootView.findViewById(R.id.textSyncStatusServerGood);
        final TextView badSyncText = rootView.findViewById(R.id.textSyncStatusServerBad);
        final Button buttonSync = rootView.findViewById(R.id.buttonSynchServer);

        if(lastSyncTimeObserver == null)
            lastSyncTimeObserver = (object, syncDate) -> {
                final int maxAgeMinutes = SchemaProvider.getDeviceConfig().getServerSyncMaxAgeMinutes();
                final long maxAgeMs = maxAgeMinutes * 60000L;
                final int maxAgeHours = maxAgeMinutes / 60;
                final Date now = new Date();

                String lastSyncTimeStr;
                long diffTime;

                if (syncDate != null) {
                    diffTime = now.getTime() - syncDate.getTime();
                    lastSyncTimeStr = SimpleDateFormat.getTimeInstance(DateFormat.SHORT).format(syncDate);
                } else {
                    diffTime = now.getTime();
                    lastSyncTimeStr = getString(R.string.server_sync_never);
                }

                boolean isSyncing = DataManager.isSyncInProgress();

                if(!isSyncing && serverSyncState == ServerSyncState.Syncing)
                    // an external syncing process (not initiated by this fragment) has stopped,
                    // maybe with success, so we check again if outdated in next condition
                    serverSyncState = ServerSyncState.Initial;

                if (Utils.isInStudyPeriod (now) && (diffTime > maxAgeMs) && serverSyncState == ServerSyncState.Initial)
                    serverSyncState = ServerSyncState.Outdated;
                else if ((diffTime <= maxAgeMs) && serverSyncState == ServerSyncState.Outdated)
                    // synchronization was externally done and finished with success
                    serverSyncState = ServerSyncState.Initial;

                if(isSyncing && (serverSyncState == ServerSyncState.Outdated || serverSyncState == ServerSyncState.Error))
                    // external syncing process is ongoing (not initiated by this fragment)
                    serverSyncState = ServerSyncState.Syncing;


                // Adapt UI to current server sync state
                // =====================================
                syncViewContainer.setVisibility(serverSyncState != ServerSyncState.Initial ? View.VISIBLE : View.GONE);

                goodSyncView.setVisibility(serverSyncState == ServerSyncState.Completed ? View.VISIBLE : View.GONE);
                if(serverSyncState == ServerSyncState.Completed)
                    goodSyncText.setText(getString(R.string.server_sync_status_good, lastSyncTimeStr));

                badSyncView.setVisibility(serverSyncState == ServerSyncState.Outdated ? View.VISIBLE : View.GONE);
                if(serverSyncState == ServerSyncState.Outdated)
                    badSyncText.setText(getString(R.string.server_sync_status_bad, maxAgeHours));

                processingSyncView.setVisibility(serverSyncState == ServerSyncState.Syncing ? View.VISIBLE : View.GONE);
                errorSyncView.setVisibility(serverSyncState == ServerSyncState.Error ? View.VISIBLE : View.GONE);

                buttonSync.setVisibility((serverSyncState == ServerSyncState.Outdated || serverSyncState == ServerSyncState.Error || serverSyncState == ServerSyncState.Syncing) ? View.VISIBLE :View.GONE);
                buttonSync.setEnabled(serverSyncState != ServerSyncState.Syncing);

                updateDailyQuestionUI();
                updateFoodlistStatusUI();
            };

        DataManager.getObservableLastSyncDate().addObserver(lastSyncTimeObserver, true, ContextCompat.getMainExecutor(getActivity()));
            // Multiple calls won't add the observer multiple times. Only one instance will be added to the list of observers.
    }


    private JSONObject obtainTodaysUserData() throws UnobtainableUserDataException {
        // Read all collected user question data from local database
        try {
            List<JSONObject> userDataObjects = DataManager.getAllDatasets(DataType.UserData);
            String anamnesisDataStr = BackendIO.getCurrentUser().anamnesisData;
            JSONObject anamnesisData = new JSONObject(anamnesisDataStr);


            String startDateStr = anamnesisData.optString("study_begin_date");
            String endDateStr = anamnesisData.optString("study_end_date");

            if(Utils.nullOrEmpty(startDateStr) || Utils.nullOrEmpty(endDateStr)) {
                throw new UnobtainableUserDataException(); // No study period specified for current user
            }

            Date startDate = Utils.setTimeToZero(Utils.getServerTimeFormat().parse(startDateStr));
            Date endDate = Utils.setTimeToNextMidnight(Utils.getServerTimeFormat().parse(endDateStr));
            Date today = new Date();


            if(today.after(endDate) || today.before(startDate)) {
                throw new UnobtainableUserDataException(); // Not in study period
            }

            today = Utils.setTimeToZero(today);

            for(JSONObject obj : userDataObjects) {
                Date effectiveDay = Utils.setTimeToZero(Utils.getServerTimeFormat().parse(obj.getString("effective_day")));
                if(today.equals(effectiveDay)) {
                    return obj;
                }
            }

            return null; // no data for today available yet.

        } catch (JSONException | ParseException | NullPointerException | DataManager.NoPermissionException e) {
            e.printStackTrace();
            throw new UnobtainableUserDataException(); // current user might not have  participant role or other error
        }
    }

    private void updateFoodlistStatusUI() {
        if(!isAdded() || getActivity() == null) {
            // verify this fragment is successfully added to fragment manager,
            // otherwise this will cause trouble when calling getLayoutInflator() from inside FoodlistCustomField.
            return;
        }

        View statusContainer = rootView.findViewById(R.id.foodlistStatusContainer);
        statusContainer.setVisibility(View.VISIBLE);

        View foodlistEmptyView = rootView.findViewById(R.id.statusFoodlistEmptyView);
        ViewGroup foodlistContainer = rootView.findViewById(R.id.foodlistElementsContainer);

        JSONObject todaysUserData = null;

        try {
            todaysUserData = obtainTodaysUserData();
        } catch (UnobtainableUserDataException e) {
            statusContainer.setVisibility(View.GONE);
            return;
        }

        boolean isFoodlistUnfilled = true;
        JSONArray foodArray = null;
        if (todaysUserData != null && todaysUserData.has("foodlist")) {

            foodArray = todaysUserData.optJSONArray("foodlist");
            if(foodArray == null) {
                // The entry seems to be typed as String, but should actually be JSONArray. I don't understand why, because everywere it should be saved as JSONArray,  but we will parse it then...
                String foodArrayStr = todaysUserData.optString("foodlist");
                if(!Utils.nullOrEmpty(foodArrayStr)) {
                    try {
                        foodArray = new JSONArray(foodArrayStr);
                    } catch (JSONException e) {
                        // can happen when foodArrayStr is null as well
                    }
                } // else: foodlist entry actually null: neither any entry has been done nor was the no consumptions checkbox set
            }

            if (foodArray != null) {
                isFoodlistUnfilled = false;
            }
        }

        if(foodlistField == null) {
            foodlistField = new FoodlistCustomField("foodlist", CustomField.FieldType.FoodList, this);
            foodlistField.setHideDescription(false);
            foodlistField.setHideNoConsumptionsCheckboxBeforeEvening(true);
            foodlistField.getObservableFieldData().addObserver((object, data) -> {
                try {
                    JSONObject todaysUserData2 = obtainTodaysUserData();
                    JSONArray foodlistData = data != null ? new JSONArray(data) : null;

                    if(todaysUserData2 == null) {
                        todaysUserData2 = new JSONObject();
                        Date today = Utils.setTimeToZero(new Date());
                        todaysUserData2.put("effective_day", Utils.getServerTimeFormat().format(today));
                    }
                    todaysUserData2.put("foodlist", foodlistData == null ? JSONObject.NULL : foodlistData);

                    DataManager.updateOrInsertData(DataType.UserData, todaysUserData2);

                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }


        foodlistEmptyView.setVisibility(isFoodlistUnfilled ? View.VISIBLE : View.GONE);
        View foodlistView = foodlistField.getView();

        if(foodlistView != null) {
            ViewParent foodListViewParent = foodlistView.getParent();
            if(foodListViewParent != null) {
                ((ViewGroup)foodListViewParent).removeAllViews();
            }
            foodlistContainer.removeAllViews();
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            foodlistView.setLayoutParams(layoutParams);
            foodlistContainer.addView(foodlistView);
        }

        foodlistField.setValue(foodArray);
    }



    private void updateDailyQuestionUI() {
        View container = rootView.findViewById(R.id.dailyQuestionStatusContainer);
        View answeredView  = rootView.findViewById(R.id.dailyQuestionAnsweredView);
        View notAnsweredView  = rootView.findViewById(R.id.dailyQuestionNotAnsweredView);
        Button buttonAnswer = rootView.findViewById(R.id.buttonDailyQuestion);

        buttonAnswer.setOnClickListener(this::onButtonDailyQuestionPress);

        if(!Utils.existQuestions(new Date())) {
            container.setVisibility(View.GONE); // No questions for today
            return;
        }

        JSONObject todaysUserData;

        try {
            todaysUserData = obtainTodaysUserData();
        } catch (UnobtainableUserDataException e) {
            container.setVisibility(View.GONE); // Not in study period or other error
            return;
        }

        boolean answered = todaysUserData != null && Utils.getUserInputState(todaysUserData) == Utils.UserInputState.COMPLETE_DATA;

        container.setVisibility(View.VISIBLE);
        answeredView.setVisibility(answered ? View.VISIBLE : View.GONE);
        notAnsweredView.setVisibility(!answered ? View.VISIBLE : View.GONE);

        buttonAnswer.setText(answered ? R.string.status_dailyquestion_button_existing_data : R.string.status_dailyquestion_button);

    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        rootView.findViewById(R.id.sensorCosinussStateContainer).setVisibility(SchemaProvider.getDeviceConfig().isCosinussUsed() ? View.VISIBLE : View.GONE);
        cosinussUI = SchemaProvider.getDeviceConfig().isCosinussUsed() ? new CosinussHomeStateUI(getContext(), rootView) : null;

        rootView.findViewById(R.id.sensorGarminStateContainer).setVisibility(SchemaProvider.getDeviceConfig().isGarminUsed() ? View.VISIBLE : View.GONE);
//        garminUI = SchemaProvider.getDeviceConfig().isGarminUsed() ? new GarminHomeStateUI(getContext(), rootView) : null;

        studyStateUI = new StudyHomeStateUI(getContext(), rootView);

        rootView.findViewById(R.id.buttonManageSensorGarmin).setOnClickListener(this::onButtonManageSensorPress);
        rootView.findViewById(R.id.buttonManageSensorCosinuss).setOnClickListener(this::onButtonManageSensorPress);
        rootView.findViewById(R.id.buttonDownloadSchemas).setOnClickListener(this::onButtonDownloadSchemasPress);
        rootView.findViewById(R.id.buttonInstallUpdate).setOnClickListener(this::onButtonInstallUpdatePress);
        rootView.findViewById(R.id.buttonParticipantManagement).setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.nav_participant_management);
        });

        rootView.findViewById(R.id.buttonSynchServer).setOnClickListener(v -> {
            if(Utils.isAnyConnection() && !Utils.isUnmeteredConnection()) {
                new AlertDialog.Builder(getContext())
                        .setCancelable(true)
                        .setPositiveButton(R.string.yes, (dialog, which) -> startSynchronization(false))
                        .setNegativeButton(R.string.no, null)
                        .setTitle(R.string.dialog_use_mobile_connection_title)
                        .setMessage(R.string.dialog_use_mobile_connection_message)
                        .show();
            } else if(Utils.isUnmeteredConnection()) { // WiFi connection
                startSynchronization(true);
            } else { // No internet connection
                new AlertDialog.Builder(getContext())
                        .setCancelable(true)
                        .setPositiveButton(R.string.ok, null)
                        .setTitle(R.string.dialog_no_connection_title)
                        .setMessage(R.string.dialog_no_connection_message)
                        .show();
            }
        });

        Button logInButton = rootView.findViewById(R.id.buttonConnect);
        logInButton.setOnClickListener(v2 -> {
            Intent i = new Intent(getActivity(), LoginActivity.class);
            getActivity().startActivity(i);
        });


        Bundle args = getArguments();
        if(args != null) {
            if (args.getBoolean(EXTRA_SCROLL_TO_COSINUSS_STATE_CONTAINER)) {
                View cosinussStateContainer = rootView.findViewById(R.id.sensorCosinussStateContainer);

                new Handler(getContext().getMainLooper()).post(() -> {
                    int top = cosinussStateContainer.getTop();
                    (rootView).scrollTo(0,top);
                });

            }
        }

    }


    private final Utils.Observer<Boolean> updateAvailableObserver = (object, updateAvailable) -> {
        ViewGroup updateContainer = rootView.findViewById(R.id.updateContainer);
        updateContainer.setVisibility(updateAvailable ? View.VISIBLE : View.GONE);
    };

    BackendIO.UserLoginStatusObserver userLoginStatusObserver = new BackendIO.UserLoginStatusObserver() {
        @Override
        public void isLoggedIn(User user) {
            generateUserSpecificView();
        }

        @Override
        public void isLoggedOut() {
            generateUserSpecificView();
        }
    };

    private void startSynchronization(boolean usingWifi) {

        serverSyncState = ServerSyncState.Syncing;
        updateServerSyncStatusUI();

        DataManager.OnSynchronizationProcessStateChangeListener serverSynchronizationStatusChangedListener = new DataManager.OnSynchronizationProcessStateChangeListener() {
            @Override
            public void onStartedUploading(int numberOfDatasetsToUpload) {
                BackendIO.serverLog(Log.INFO, LOG_TAG,"Started manual data synchronization with f4f backend by pressing manual sync button. Using Wi-Fi: " + usingWifi);
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
                BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual Server Sync cancelled.");
                serverSyncState = ServerSyncState.Initial;
                getActivity().runOnUiThread(HomeFragment.this::updateServerSyncStatusUI);
            }

            @Override
            public void onSynchronizationCompleted(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded) {
                BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual sync with f4f backend server completed. Datasets Uploaded: " + numberOfDatasetsUploaded + ", Datasets downloaded: " + numberOfDatasetsDownloaded);
                serverSyncState = ServerSyncState.Completed;
                getActivity().runOnUiThread(HomeFragment.this::updateServerSyncStatusUI);
            }

            @Override
            public void onSynchronizationError() {
                BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual Server Sync failed. (SynchronizationError).");
                serverSyncState = ServerSyncState.Error;
                getActivity().runOnUiThread(HomeFragment.this::updateServerSyncStatusUI);
            }
        };

        // Always update server schemas before data sync
        SchemaProvider.downloadSturcturesFromServer((withError, configUpdated) ->
        {
            if (configUpdated) {
                // This will only be the case when the device configuration was updated on server
                // since the last time schemas were downloaded.

                // In case sensor device configuration have been changed,
                // apply it to connected wearables.
                StudyCompanion.updateSensorConfig();
            }

            // in case auto-sync configuration has been changed, re-initialize all workers
            WorkerBase.initAllWorkers();

            try {
                DataManager.startSynchronization(serverSynchronizationStatusChangedListener);
            } catch (DataManager.NoPermissionException e) {
                Toast.makeText(getContext(), R.string.error_no_participant, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onButtonDownloadSchemasPress(View v) {
        Button btnDownload = rootView.findViewById(R.id.buttonDownloadSchemas);
        View viewGood = rootView.findViewById(R.id.serverSchemaDownloadViewGood);
        View viewActive = rootView.findViewById(R.id.serverSchemaDownloadViewActive);
        View viewBad = rootView.findViewById(R.id.serverSchemaDownloadViewBad);

        btnDownload.setEnabled(false);
        viewActive.setVisibility(View.VISIBLE);
        viewGood.setVisibility(View.GONE);
        viewBad.setVisibility(View.GONE);

        SchemaProvider.downloadSturcturesFromServer((withError, configUpdated) ->
        {
            if (configUpdated) {
                // This will only be the case when the device configuration was updated on server
                // since the last time schemas were downloaded.

                // In case sensor device configuration have been changed,
                // apply it to connected wearables.
                StudyCompanion.updateSensorConfig();
            }

            viewBad.setVisibility(withError ? View.VISIBLE : View.GONE);
            viewGood.setVisibility(!withError ? View.VISIBLE : View.GONE);
            viewActive.setVisibility(View.GONE);
            btnDownload.setEnabled(true);
        });
    }

    private void onButtonInstallUpdatePress(View v) {
        boolean hasPermission = AppUpdater.checkOrObtainPackageInstallPermission(this, REQUEST_INSTALL_PACKAGE_PERMISSION, true);
        if(hasPermission) {
            AppUpdater.installUpdatedApk(getActivity());
        }
    }



    private void onButtonManageSensorPress(View button) {
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
//        SensorManagementFragment.SensorType sensorType = SensorManagementFragment.SensorType.Garmin;
//        if(button.getId() == R.id.buttonManageSensorCosinuss) {
//                sensorType = SensorManagementFragment.SensorType.Cosinuss;
//        }
        Bundle bundle = new Bundle();
//        bundle.putString(SensorManagementFragment.EXTRA_SENSOR_TYPE, sensorType.toString());
        bundle.putString(SensorManagementFragment.EXTRA_SENSOR_TYPE, SensorManagementFragment.SensorType.Cosinuss.toString());

        navController.navigate(R.id.nav_sensor_management, bundle);
    }

    private void onButtonDailyQuestionPress(View button) {
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);

        Bundle bundle = new Bundle();
        bundle.putBoolean(QuestionsFragment.EXTRA_OPEN_TODAY_QUESTIONS, true);

        navController.navigate(R.id.nav_daily_questions, bundle);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (lastSyncTimeObserver != null) {
            DataManager.getObservableLastSyncDate().removeObserver(lastSyncTimeObserver);
            lastSyncTimeObserver = null;
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        if(serverSyncState != ServerSyncState.Syncing)
            serverSyncState = ServerSyncState.Initial;

        BackendIO.addUserLoginStatusObserver(userLoginStatusObserver);
        AppUpdater.getUpdatedApkReadyToInstallState().addObserver(updateAvailableObserver, true, ContextCompat.getMainExecutor(getActivity()));

        if(cosinussUI != null)
            cosinussUI.onResume();

//        if(garminUI != null)
//            garminUI.onResume();

        studyStateUI.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        BackendIO.removeUserLoginStatusObserver(userLoginStatusObserver);
        AppUpdater.getUpdatedApkReadyToInstallState().removeObserver(updateAvailableObserver);

        if(cosinussUI != null)
            cosinussUI.onPause();

//        if(garminUI != null)
//            garminUI.onPause();

        studyStateUI.onPause();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(foodlistField != null) {
            foodlistField.handleActivityResult(requestCode, resultCode, data);
        }

        if(requestCode == REQUEST_INSTALL_PACKAGE_PERMISSION) {
            boolean hasPermission = AppUpdater.checkOrObtainPackageInstallPermission(this, REQUEST_INSTALL_PACKAGE_PERMISSION, false);
            if(!hasPermission) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.app_updater_permission_dialog_title)
                        .setMessage(R.string.app_updater_permission_dialog_msg_denied)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            } else {
                AppUpdater.installUpdatedApk(getActivity());
            }
        }
    }
}