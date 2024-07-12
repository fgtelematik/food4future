package de.thwildau.f4f.studycompanion.ui.dev;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Date;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.AppUpdater;
import de.thwildau.f4f.studycompanion.datamodel.DataManager;
import de.thwildau.f4f.studycompanion.notifications.NotificationOrganizer;
import de.thwildau.f4f.studycompanion.ui.customform.ListCustomFieldEditorActivity;
import de.thwildau.f4f.studycompanion.ui.customform.ListCustomFieldEditorFragment;

@SuppressLint("SetTextI18n")
public class DevFragment extends Fragment {

    private static final String LOG_TAG = "DevFragment";
    private static final int REQUEST_INSTALL_PACKAGE_PERMISSION = 50;
    private static final int REQUEST_LIST_EDITOR = 51;

    private String currentListData = null;

    private View rootView;

    public DevFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        rootView =  inflater.inflate(R.layout.fragment_dev, container, false);

        rootView.findViewById(R.id.btnShowListEditor).setOnClickListener(this::showListEditor);
        rootView.findViewById(R.id.btnShowCosinussReminder).setOnClickListener(this::showCosinussReminder);
        rootView.findViewById(R.id.btnDownloadUpdate).setOnClickListener(this::downloadUpdate);
        rootView.findViewById(R.id.btnInstallUpdate).setOnClickListener(this::installUpdate);


        Log.d(LOG_TAG, "DevFragment loaded.");

        return rootView;
    }


    private void showUserInputReminder(View view) {
        try {
            JSONObject todaysQuestions = DataManager.getUserDataForEffectiveDay(new Date());
            if(     Utils.isInStudyPeriod(new Date()) &&
                    Utils.getUserInputState(todaysQuestions) != Utils.UserInputState.COMPLETE_DATA) {
                NotificationOrganizer.showUserInputReminder();
            }
        } catch (DataManager.NoPermissionException e) {
            // do not show user input reminder if not logged in or not participant
        }

    }

    private void showCosinussReminder(View v) {
        NotificationOrganizer.showCosinussReminder();
    }


    private void downloadUpdate(View v) {
        AppUpdater.tryDownloadNewApk();
    }

    private void installUpdate(View v) {
        boolean hasPermission = AppUpdater.checkOrObtainPackageInstallPermission(this, REQUEST_INSTALL_PACKAGE_PERMISSION, true);
        if(hasPermission) {
            AppUpdater.installUpdatedApk(getActivity());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        } else if(requestCode == REQUEST_LIST_EDITOR) {
            String updatedListData = data.getStringExtra(ListCustomFieldEditorFragment.EXTRA_DATA);
            if(updatedListData != null) {
                currentListData = updatedListData;
            }
        }
    }

    private final Utils.Observer<Boolean> updateAvailableObserver = (object, updateAvailable) -> {
        TextView textState = rootView.findViewById(R.id.textUpdateInstallableState);
        textState.setText("Installable APK available: " + (updateAvailable ? "yes" : "no"));
    };

    private final Utils.Observer<AppUpdater.ApkUpdateState> updateDownloadObserver = (object, updateDownloadState) -> {
        TextView textState = rootView.findViewById(R.id.textUpdateDownloadState);
        switch(updateDownloadState) {
            case IDLE:
                textState.setText("Download State: Idle");
                break;
            case FINISH_ERROR:
                textState.setText("Download State: Download terminated with an error.");
                break;
            case FINISH_SUCCESS_OR_NO_DOWNLOAD_NEEDED:
                textState.setText("Download State: Download finished or no update available.");
                break;
            case DOWNLOADING:
                textState.setText("Download State: Downloading APK...");
                break;
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        AppUpdater.getApkDownloadState().addObserver(updateDownloadObserver, true, ContextCompat.getMainExecutor(getActivity()));
        AppUpdater.getUpdatedApkReadyToInstallState().addObserver(updateAvailableObserver, true, ContextCompat.getMainExecutor(getActivity()));
    }

    @Override
    public void onPause() {
        super.onPause();
        AppUpdater.getApkDownloadState().removeObserver(updateDownloadObserver);
        AppUpdater.getUpdatedApkReadyToInstallState().removeObserver(updateAvailableObserver);

    }

    private void showListEditor(View v) {
        if(currentListData == null) {
            currentListData =
                    "[]";
        }
        String listSchema = "{ \"label\": \"Testliste\", \"helpText\" : \"Dies ist eine Testliste\", " +
                "\"datatype\" : \"ListType\", \"elements_type\" : \"ADT\", \"adt_enum_id\" : \"anamnesis_data\" }";
//                "\"datatype\" : \"ListType\", \"elements_type\" : \"IntType\"}";

        Intent i = new Intent(getActivity(), ListCustomFieldEditorActivity.class);
        i.putExtra(ListCustomFieldEditorFragment.EXTRA_DATA, currentListData);
        i.putExtra(ListCustomFieldEditorFragment.EXTRA_LIST_SCHEMA_JSON, listSchema);

        startActivityForResult(i, REQUEST_LIST_EDITOR);
    }


}