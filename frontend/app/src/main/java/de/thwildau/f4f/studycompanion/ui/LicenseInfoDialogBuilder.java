package de.thwildau.f4f.studycompanion.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.datamodel.StaticResources;

public class LicenseInfoDialogBuilder extends AlertDialog.Builder {
    private final Context mActivity;
    private AlertDialog mDialog;

    public LicenseInfoDialogBuilder(@NonNull AppCompatActivity baseActivity) {
        super(baseActivity);
        mActivity = baseActivity;
    }

    public LicenseInfoDialogBuilder(@NonNull AppCompatActivity baseActivity, int themeResId) {
        super(baseActivity, themeResId);
        mActivity = baseActivity;
    }

    @NonNull
    @Override
    public AlertDialog create() {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        ListView mainView = (ListView)inflater.inflate(R.layout.dialog_license_info, null);
        mainView.setOnItemClickListener(this::onItemClick);
        setTitle(R.string.action_license_info);
        setView(mainView);

        mDialog = super.create();

        return mDialog;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i;
        switch((int)id) {
            case 0:
                OssLicensesMenuActivity.setActivityTitle(mActivity.getString(R.string.action_oss_license_info));
                i = new Intent(mActivity, OssLicensesMenuActivity.class);
                mActivity.startActivity(i);
                break;
            case 1:
                i = new Intent(mActivity, TextViewActivity.class);
                i.putExtra(TextViewActivity.EXTRA_TITLE_RESOURCE_ID, R.string.license_info_garmin_title);
                i.putExtra(TextViewActivity.EXTRA_TEXT_RESOURCE_ID, R.string.license_info_garmin);
                mActivity.startActivity(i);
                break;
            case 2:
                i = new Intent(mActivity, TextViewActivity.class);
                i.putExtra(TextViewActivity.EXTRA_TITLE_RESOURCE_ID, R.string.action_image_license_info);
                i.putExtra(TextViewActivity.EXTRA_STATIC_RESOURCE_PARCELABLE, StaticResources.IMAGE_LICENSE_INFO_HTML);
                mActivity.startActivity(i);
                break;
        }
        mDialog.dismiss();

    }




}
