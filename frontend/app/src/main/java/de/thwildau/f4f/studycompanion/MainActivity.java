package de.thwildau.f4f.studycompanion;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.thwildau.f4f.studycompanion.backend.AppUpdater;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.background.WorkerBase;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.StaticResourcesProvider;
import de.thwildau.f4f.studycompanion.datamodel.User;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import de.thwildau.f4f.studycompanion.ui.DefaultExceptionHandler;
import de.thwildau.f4f.studycompanion.ui.LicenseInfoDialogBuilder;
import de.thwildau.f4f.studycompanion.ui.home.HomeFragment;
import de.thwildau.f4f.studycompanion.ui.preferences.SettingsActivity;
import de.thwildau.f4f.studycompanion.ui.questions.QuestionsFragment;
//import de.thwildau.f4f.studycompanion.ui.sensors.SensorManagementFragment;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "F4F_MAIN_ACTIVITY";

    private static final int PERMISSION_REQUESTS = 1;

    private AppBarConfiguration mAppBarConfiguration;
    private Menu navMenu;
    private boolean userStatusChangedInBackground = false;
    private boolean activityInForeground = true;
    private boolean resourceCacheUpdated = false;

    private User currentUser = null;

    private BackendIO.UserLoginStatusObserver loginStatusObserver;

    private boolean quitRequest = false;
    private final static int QUIT_DELAY_MILLISECONDS = 2000;

    private int f4fTapCount = 0;
    private int thwiTapCount = 0;


    /**
     * Make only navigation items visible, that are meant for non logged-in user.
     */
    private void updateNavigationElementsByUserStatus() {
        updateNavigationElementsByUserStatus(null);
    }

    /**
     * Make only navigation items visible to be seen by current user depending on his role.
     * @param user
     */
    private void updateNavigationElementsByUserStatus(User user) {
        ArrayList<Integer> visibleNavElements = new ArrayList<>();

        if(user == null) {
            // elements visible for users not logged in:
            visibleNavElements.add(R.id.nav_status);
            visibleNavElements.add(R.id.nav_login);

        } else {
            List<String> activeSensors = SchemaProvider.getDeviceConfig().getSensorsUsed();
            boolean isUsingSensors = !activeSensors.isEmpty() && !(
                    activeSensors.size() == 1 && activeSensors.get(0).toLowerCase().equals("garmin")
                    ); // sensor list is not empty and Garmin is not the only selected one
                       // (Garmin support is disabled in this branch)

            switch (user.role) {
                case Administrator:
                    visibleNavElements.add(R.id.nav_status);
                    visibleNavElements.add(R.id.nav_user_management);
                    visibleNavElements.add(R.id.nav_dev);
                    if(!SchemaProvider.getDeviceConfig().getSensorsUsed().isEmpty())
                        visibleNavElements.add(R.id.nav_sensor_management);
                    break;
                case Nurse:
                    visibleNavElements.add(R.id.nav_status);
                    visibleNavElements.add(R.id.nav_participant_management);
                    break;
                case Participant:
                    visibleNavElements.add(R.id.nav_status);
                    visibleNavElements.add(R.id.nav_participant_profile);
                    visibleNavElements.add(R.id.nav_daily_questions);
                    if(isUsingSensors)
                        visibleNavElements.add(R.id.nav_sensor_management);
                    visibleNavElements.add(R.id.nav_qr);
                    break;
                case Supervisor:
                    visibleNavElements.add(R.id.nav_status);
                    if(!SchemaProvider.getDeviceConfig().getSensorsUsed().isEmpty())
                        visibleNavElements.add(R.id.nav_sensor_management);
            }
        }

        try {
            runOnUiThread(() -> {
                    for(int i = 0; i < navMenu.size(); i++) {
                        MenuItem item = navMenu.getItem(i);
                        boolean itemVisible = visibleNavElements.contains(new Integer(item.getItemId()));
                        item.setVisible(itemVisible);
                    }

                    NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);

                    // switch to home fragment, if current nav destination is no longer available
                    NavDestination currentDestination = navController.getCurrentDestination();
                    if(currentDestination != null) {
                        if(!visibleNavElements.contains(currentDestination.getId())) {
                            navController.navigate(R.id.nav_status);
                        }
                    }
            });

        } catch(Exception e) {
            Log.w(LOG_TAG, "Error at trying to update navigation view. UI thread might be inactive.");
            e.printStackTrace();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        currentUser = BackendIO.getCurrentUser();
        updateNavigationElementsByUserStatus(currentUser);

        activityInForeground = true;

        // Update schemas + config on every resume.
        // The schemas might already be downloading if onCreate was called right before and
        // the user is logged in (initiated by the call to BackendIO.addUserLoginStatusObserver() ).
        // In this case, following call results in a noop.
        // Structures might only be successfully downloaded if authenticated in future API version,
        // but this should not be a problem.
        if(currentUser != null)
            SchemaProvider.downloadSturcturesFromServer(null);

        if(userStatusChangedInBackground) {
            // logout was triggered while executing a background worker while the activity was
            // running in  background (e.g. if user session expired or remotely logged out)
            updateNavigationElementsByUserStatus(BackendIO.getCurrentUser());
            userStatusChangedInBackground = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityInForeground = false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DefaultExceptionHandler.toCatch(this);

        // Init Server Backend Interface (incl. implicit user log-in)
        BackendIO.initialize(StudyCompanion.getAppContext());

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navMenu = navigationView.getMenu();

        Calendar buildTimeCal = Calendar.getInstance();
        buildTimeCal.setTime(new Date(Long.parseLong(BuildConfig.BUILD_TIME)));
        ((TextView) findViewById(R.id.textAppInfoCopyright)).setText(getString(R.string.app_info_copyright, buildTimeCal.get(Calendar.YEAR)));

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_status,
                R.id.nav_user_management,
                R.id.nav_daily_questions,
                R.id.nav_participant_management,
                R.id.nav_sensor_management,
                R.id.nav_participant_profile,
                R.id.nav_dev,
                R.id.nav_qr)
                .setDrawerLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        final TextView subtitleText = navigationView.getHeaderView(0).findViewById(R.id.tvSubtitle);

        loginStatusObserver = new BackendIO.UserLoginStatusObserver() {
            @Override
            public void isLoggedIn(User user) {

                subtitleText.setText(getResources().getString(R.string.session_status_logged_in, user.username));
                if (!activityInForeground)
                    userStatusChangedInBackground = true;

                // Do we need "else" here? Otherwise we might try to update UI when in background, which should raise the
                // exception in the next method.
                updateNavigationElementsByUserStatus(user);
                if (currentUser == null) {
                    BackendIO.serverLog(Log.INFO, LOG_TAG, "User logged in. WiFi: " + Utils.isUnmeteredConnection());
                }

                StaticResourcesProvider.updateResourceCache(MainActivity.this);
                resourceCacheUpdated = true;

                currentUser = user;

                SchemaProvider.downloadSturcturesFromServer((withError, configUpdated) ->
                {
                    if (configUpdated) {
                        // This will only be the case when the device configuration was updated on server
                        // since the last time schemas were downloaded.
                        StudyCompanion.updateSensorConfig();

//                        if(StudyCompanion.getGarminSensorManager().getObservableSdkInitializationError().getValue()) {
//                            // Garmin SDK was not initialized yet, maybe the SDK key was not yet present,
//                            // so try again.
//                            StudyCompanion.getGarminSensorManager().init();
//                            StudyCompanion.getGarminSensorManager().start();
//                        }
                    }

                    if (Utils.isUnmeteredConnection()) {
                        // Trigger server sync after Login (esp. for auto-fetching User Input)
                        try {
                            DataManager.startSynchronization(synchronizationProcessStateChangeListener);
                        } catch (DataManager.NoPermissionException e) {
                            e.printStackTrace();
                        }

                        // Check for new APK version and download in background, if available
                        AppUpdater.tryDownloadNewApk();
                    }

                    // in case auto-sync configuration has been changed, re-initialize all workers
                    WorkerBase.initAllWorkers();
                });

                // Wipe all local sensor data , which were already synchronized with backend server
                // in background to reduce device space usage. Can be 100's of MB on the very first time,
                // after installing App update, therefore we run it as background thread.
                new Thread(() -> {
                    try {
                        DataManager.wipeAllSynchronizedSensorData();
                    } catch (DataManager.NoPermissionException e) {
                        // ignore
                    }
                }).start();
            }

            @Override
            public void isLoggedOut() {
                updateNavigationElementsByUserStatus();
                if (!activityInForeground) userStatusChangedInBackground = true;
                subtitleText.setText(R.string.session_status_logged_out);
                if (currentUser != null) {
                    BackendIO.serverLog(Log.INFO, LOG_TAG, "User signed off! (UserID: " + currentUser.id + ")");
                    DataManager.wipeAllLocalDataForUser(currentUser);
                    currentUser = null;
                }

                if(!resourceCacheUpdated)
                    StaticResourcesProvider.updateResourceCache(MainActivity.this);
            }
        };

        AppUpdater.showUpdateReleaseNotesIfNecessary(this, false);


        BackendIO.addUserLoginStatusObserver(loginStatusObserver);

        getRuntimePermissions(true);
        processIntent(getIntent());
    }


    private void exportData(int suffixCount) {
        String destFileName =  "default.realm";

        try {
            File dataDir = getApplicationContext().getDataDir();
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);


            File sourceFile = new File(dataDir.getAbsolutePath() + "/files/default.realm");
            File destFile = new File(downloadDir.getAbsolutePath() + "/" + destFileName);

            if(suffixCount != 0) {
                destFileName = "default-" + suffixCount + ".realm";
                destFile = new File(downloadDir.getAbsolutePath() + "/" + destFileName);
            }

            Utils.copyFile(sourceFile, destFile);

            Toast.makeText(getApplicationContext(), "Data stored as '"+destFileName+"' in Downloads.", Toast.LENGTH_LONG).show();

        } catch(IOException e) {
            Log.e(LOG_TAG, "Error during data Export: " + e);

            if(e instanceof FileNotFoundException && suffixCount<100) {
                // Following line is a workaround for a problem with Android >12 described here:
                // https://learn.microsoft.com/en-us/answers/questions/932579/after-manually-delete-a-file-and-use-fileoutputstr.html
                // circumventing this without the need of the MANAGE_EXTERNAL_STORAGE permission.

                // This limits the data export with following manual file deletion to a number of 100 attempts,
                // which should be enough since triggering data export should be an absolute exception case.

                runOnUiThread(() -> exportData(suffixCount + 1));
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Data Export failed")
                    .setMessage("Exception:\n" + e)
                    .setPositiveButton(R.string.ok, null)
                    .show();

            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem exportDataMenuItem = menu.findItem(R.id.action_export_data);
        ImageView f4fLogo = findViewById(R.id.f4fLogo);
        ImageView thwiLogo = findViewById(R.id.ThWiLogo);
        exportDataMenuItem.setVisible(false);

        View.OnClickListener logoClickListener = v -> {
            if(v == f4fLogo) {
                if(thwiTapCount != 5)
                    thwiTapCount = 0;
                f4fTapCount++;
            } else if(v  == thwiLogo) {
                if(f4fTapCount != 5)
                    f4fTapCount = 5;
                thwiTapCount++;
            }

            exportDataMenuItem.setVisible(f4fTapCount == 5 && thwiTapCount == 5);
        };

        if(f4fLogo == null || thwiLogo == null)
            // might happen when the activity is started in background
            return true;

        f4fLogo.setOnClickListener(logoClickListener);
        thwiLogo.setOnClickListener(logoClickListener);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if(itemId == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        } else if(itemId == R.id.action_license_info) {
            new LicenseInfoDialogBuilder(this).show();
        } else if(itemId == R.id.action_export_data) {
            exportData(0);
        } else if(itemId == R.id.action_release_notes) {
            AppUpdater.showUpdateReleaseNotesIfNecessary(this, true);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        if(navController.getCurrentDestination().getId() == R.id.nav_status) {

            // Handle delayed quit on back press
            if(quitRequest) {
                finishAndRemoveTask();
            } else {
                quitRequest = true;
                Toast.makeText(this, R.string.quit_delay_msg, Toast.LENGTH_SHORT).show();
                final Handler handler = new Handler();
                handler.postDelayed(() -> quitRequest = false, QUIT_DELAY_MILLISECONDS);
            }


        } else {
            super.onBackPressed();
        }
    }


    private void requestIgnoreBatteryOptimization() {
        // Show a system UI dialog requesting to put this app on the battery optimization whitelist.
        // If it's not on this list,
        // background tasks for server and sensor synchronization will not work as expected.

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void serverLogUngrantedPermissions(@NonNull List<String> ungrantedPermissions) {
        if(ungrantedPermissions.isEmpty()) {
            return;
        }

        StringBuilder logMsg = new StringBuilder("Repeatedly asked for granting permissions:");

        for (String permission : ungrantedPermissions) {
            logMsg.append("\n\t").append(permission);
        }

        BackendIO.serverLog(Log.WARN, LOG_TAG, logMsg.toString());
    }

    private void getRuntimePermissions(boolean firstAttempt) {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {

                // skip ACCESS_BACKGROUND_LOCATION for devices using Android 9 or lower, since it
                // is always denied. See https://stackoverflow.com/a/60550536/5106474
                if(permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                        android.os.Build.VERSION.SDK_INT <= 29
                ) {
                    continue;
                }

                // skip the new Bluetooth Permissions for devices using Android 11 or lower, since it
                if((permission.equals(Manifest.permission.BLUETOOTH_SCAN) ||  permission.equals(Manifest.permission.BLUETOOTH_CONNECT))
                        && android.os.Build.VERSION.SDK_INT <= 30
                ) {
                    continue;
                }

                // skip POST_NOTIFICATIONS for devices using Android 12 or lower, since it
                // is always denied. see: https://developer.android.com/develop/ui/views/notifications/notification-permission
                if(permission.equals(Manifest.permission.POST_NOTIFICATIONS) &&
                        android.os.Build.VERSION.SDK_INT <= 32
                ) {
                    continue;
                }


                allNeededPermissions.add(permission);
            }
        }

        if(android.os.Build.VERSION.SDK_INT >= 30) {
            if ((allNeededPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) || allNeededPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
                    && allNeededPermissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                // If Background location and foreground location is requested in the same time, the whole request will be ignored on target SDK > 30
                // see https://stackoverflow.com/questions/64388343/activitycompat-requestpermissions-for-targetsdkversion-30-is-not-working

                // so, we won't ask for background location in first attempt
                allNeededPermissions.remove(Manifest.permission.ACCESS_BACKGROUND_LOCATION);

            }
        }

        // The REQUEST_INSTALL_PACKAGES permission is handled separately
        // when/if it comes to App update installations, so we remove it from this list
        allNeededPermissions.remove(Manifest.permission.REQUEST_INSTALL_PACKAGES);

        if(!firstAttempt) {
            serverLogUngrantedPermissions(allNeededPermissions);

            // ignore ungranted camera permission on second attempt
            if(allNeededPermissions.contains(Manifest.permission.CAMERA)) {
                allNeededPermissions.remove(Manifest.permission.CAMERA);
                BackendIO.serverLog(Log.WARN, LOG_TAG,"\t...ignore denied camera permission");
            }

        }

        if (!allNeededPermissions.isEmpty()) {

            int request_msg = firstAttempt ? R.string.permissions_request_first_attempt : R.string.permissions_request_second_attempt;

            if (allNeededPermissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION) && allNeededPermissions.size() == 1)
                request_msg = R.string.permissions_request_background_location;

            new AlertDialog.Builder(this)
                    .setMessage(request_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS))
                    .setNegativeButton(R.string.Deny, (dialog, which) -> finishAndRemoveTask())
                    .show();

        } else {
            // After all permissions are gained, request ignore battery optimization.
            requestIgnoreBatteryOptimization();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUESTS) {
            getRuntimePermissions(false);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void processIntent(Intent i) {
        Bundle ie = i.getExtras();
        if(ie == null) {
            return;
        }

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);

        if (ie.containsKey(NotificationOrganizer.EXTRA_NOTIFICATION_ID)) {
            // Navigate to today's question form, if coming from reminder notification tap
            int notificationId = ie.getInt(NotificationOrganizer.EXTRA_NOTIFICATION_ID);
            Bundle bundle = new Bundle();

            if(notificationId == NotificationOrganizer.NOTIFICATION_ID_USER_INPUT_REMINDER) {
                bundle.putBoolean(QuestionsFragment.EXTRA_OPEN_TODAY_QUESTIONS, true);
                navController.navigate(R.id.nav_daily_questions, bundle);
//            } else if(notificationId == NotificationOrganizer.SyncType.SensorSync.getNotificationId()) {
//                bundle.putString(SensorManagementFragment.EXTRA_SENSOR_TYPE, SensorManagementFragment.SensorType.Garmin.toString());
//                navController.navigate(R.id.nav_sensor_management, bundle);
            } else if(notificationId == NotificationOrganizer.NOTIFICATION_ID_COSINUSS_CONNECTED) {
                bundle.putBoolean(HomeFragment.EXTRA_SCROLL_TO_COSINUSS_STATE_CONTAINER, true);
                navController.navigate(R.id.nav_status, bundle);
            } else if(notificationId == NotificationOrganizer.NOTIFICATION_ID_UPDATE_AVAILABLE) {
                navController.navigate(R.id.nav_status);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // called when notification was tapped!
        processIntent(intent);
    }

    private final DataManager.OnSynchronizationProcessStateChangeListener synchronizationProcessStateChangeListener = new DataManager.OnSynchronizationProcessStateChangeListener() {
        @Override
        public void onStartedUploading(int numberOfDatasetsToUpload) {
            BackendIO.serverLog(Log.INFO, LOG_TAG,"Started manual data synchronization with f4f backend due to App launch.");
        }

        @Override
        public void onStartedDownloading() {
            Log.i(LOG_TAG, "Downloading data from server.");
        }

        @Override
        public void onSynchronizationProgress(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded) { }

        @Override
        public void onSynchronizationCancelled() {
            BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual Server Sync cancelled.");
        }

        @Override
        public void onSynchronizationCompleted(int numberOfDatasetsUploaded, int numberOfDatasetsDownloaded) {
            BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual sync with f4f backend server completed. Datasets Uploaded: " + numberOfDatasetsUploaded + ", Datasets downloaded: " + numberOfDatasetsDownloaded);
        }

        @Override
        public void onSynchronizationError() {
            BackendIO.serverLog(Log.INFO, LOG_TAG,"Manual Server Sync failed. (SynchronizationError).");
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BackendIO.removeUserLoginStatusObserver(loginStatusObserver);
    }
}