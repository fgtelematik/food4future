package de.thwildau.f4f.studycompanion.ui.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.URLUtil;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import de.thwildau.f4f.studycompanion.BuildConfig;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.sensors.interfaces.SensorConnectionState;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.action_settings);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private Preference sensorFirmwareUpdatePref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            Preference backendServer = findPreference("backend_server");
            Preference backendCustomServer = findPreference("backend_custom_server");
            Preference backendServerUrlView = findPreference("backend_server_url_view");
            Preference appVersionString = findPreference("app_version_string");
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

            boolean isReleaseBuild = StudyCompanion.isReleaseBuild();

//
//            if(isReleaseBuild) {
//                // In Release mode the server is not changeable through the app.
//                // The Backend Endpoint is hard-coded to: R.string.server_endpoint_release
//                String backendServerUrl = getString(R.string.server_endpoint_release);
//                backendServer.setSummaryProvider(null);
//                backendServer.setSummary(getString(R.string.server_name_release));
//                backendServerUrlView.setSummary(backendServerUrl);
//                backendServer.setEnabled(false);
//                backendServerUrlView.setVisible(true);
//                backendCustomServer.setVisible(false);
//            } else {
//            }

            boolean isCustomServer = sp.getString("backend_server", "").equals("custom");
            String currentServerUrl = BackendIO.getServerUrl();

            backendCustomServer.setVisible(isCustomServer);
            backendServerUrlView.setVisible(!isCustomServer);
            backendServerUrlView.setSummary(currentServerUrl);

            if(isReleaseBuild) {
                backendServer.setEnabled(false);
                backendCustomServer.setEnabled(false);
                backendServer.setSummaryProvider(null);
                if(getString(R.string.server_endpoint_release).equals(currentServerUrl)) {
                    backendServer.setSummary(getString(R.string.server_name_release));
                } else {
                    backendServer.setSummary("custom");
                }
            } else {
                backendServer.setOnPreferenceChangeListener((preference, newValue) ->
                {
                    backendCustomServer.setVisible(newValue.equals("custom"));
                    backendServerUrlView.setVisible(!newValue.equals("custom"));
                    backendServerUrlView.setSummary(newValue.toString());

                    return true;
                });

                backendCustomServer.setOnPreferenceChangeListener((preference, newValue) ->
                        newValue != null && URLUtil.isValidUrl(newValue.toString()));
            }

            Preference logoutPref = findPreference("logout");
            if(BackendIO.getCurrentUser() == null) {
                logoutPref.setEnabled(false);
            }
            logoutPref.setOnPreferenceClickListener(preference ->
            {
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.setting_disconnect_confirm_message)
                        .setCancelable(true)
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                            BackendIO.logout();
                            logoutPref.setEnabled(false);
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();

                return true;
            });

            sensorFirmwareUpdatePref = findPreference("sensor_firmware_update");
//            sensorFirmwareUpdatePref.setOnPreferenceClickListener(this::onSensorFirmwareUpdatePreferenceClick);
            sensorFirmwareUpdatePref.setVisible(false); // comment, if Garmin is integrated

            appVersionString.setTitle(BuildConfig.VERSION_NAME);
        }

        private final Utils.Observer<SensorConnectionState> sensorConnectedObserver = new Utils.Observer<SensorConnectionState>() {
            @Override
            public void onUpdate(Utils.ObservableValue<SensorConnectionState> object, SensorConnectionState sensorConnected) {
                sensorFirmwareUpdatePref.setEnabled(sensorConnected.equals(SensorConnectionState.CONNECTED));
            }
        };

        @Override
        public void onStart() {
            super.onStart();
//            StudyCompanion.getGarminSensorManager().getObservableConnectionState().addObserver(sensorConnectedObserver, true, ContextCompat.getMainExecutor(getActivity()));
        }

        @Override
        public void onStop() {
            super.onStop();
//            StudyCompanion.getGarminSensorManager().getObservableConnectionState().removeObserver(sensorConnectedObserver);
        }

//        private boolean onSensorFirmwareUpdatePreferenceClick(Preference pref) {
//            SensorManagerBase sensorManager = StudyCompanion.getGarminSensorManager();
//
//            Toast.makeText(getActivity(), R.string.firmware_update_search , Toast.LENGTH_SHORT).show();
//
//            ISensorFirmwareUpdateProcessCallback firmwareUpdateProcessCallback = new ISensorFirmwareUpdateProcessCallback() {
//                @Override
//                public void onFirmwareUpdateAvailable() {
//                    getActivity().runOnUiThread(() -> {
//                        new AlertDialog.Builder(getActivity())
//                                .setMessage(R.string.firmware_update_availalbe_ask_install)
//                                .setPositiveButton(R.string.yes, (dialog, which) -> sensorManager.installFirmwareUpdate(this))
//                                .setNegativeButton(R.string.no, null)
//                                .show();
//                    });
//                }
//
//                @Override
//                public void onFirmwareUpdateNotAvailable() {
//                    getActivity().runOnUiThread(() -> {
//                        new AlertDialog.Builder(getActivity())
//                                .setMessage(R.string.firmware_update_not_available)
//                                .setPositiveButton(R.string.ok, null)
//                                .show();
//                    });
//                }
//
//                @Override
//                public void onFirmwareUpdateQueued() {
//                    getActivity().runOnUiThread(() -> {
//                    Toast.makeText(getActivity(), R.string.firmware_update_queued, Toast.LENGTH_LONG).show();
//                    });
//                }
//
//                @Override
//                public void onError(Throwable e) {
//                    e.printStackTrace();
//                    getActivity().runOnUiThread(() -> {
//                        Toast.makeText(getActivity(), getString(R.string.firmware_update_error, e.toString()) , Toast.LENGTH_SHORT).show();
//                    });
//                }
//            };
//
//            sensorManager.checkForFirmwareUpdate(firmwareUpdateProcessCallback);
//
//
//            return true;
//        }



    }



    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}