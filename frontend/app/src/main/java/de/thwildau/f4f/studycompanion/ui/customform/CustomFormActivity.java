package de.thwildau.f4f.studycompanion.ui.customform;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;

public class CustomFormActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        // Configure Toolbar, set Back Button and Title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        String title = getIntent().getStringExtra(CustomFormFragment.EXTRA_TITLE);
        getSupportActionBar().setTitle(title != null ? title : getString(R.string.generic_custom_form_title));

        CustomFormFragment customFormFragment;

        if(savedInstanceState == null) {
            // create and include CustomFormFragment
            customFormFragment = CustomFormFragment.newInstance(getIntent().getExtras(), customFormCallback);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, customFormFragment)
                    .commit();
        } else {
            customFormFragment = (CustomFormFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if(customFormFragment != null) {
                customFormFragment.setCustomFormCallback(customFormCallback);
            }
        }
    }

    private final Utils.FragmentWrapperActivityCallback customFormCallback = (cancelled, updatedDataJson) -> {
        if(cancelled) {
            setResult(Activity.RESULT_CANCELED);
        } else {
            Intent i = new Intent();
            i.putExtra(CustomFormFragment.EXTRA_UPDATED_DATA_JSON, updatedDataJson);
            setResult(Activity.RESULT_OK, i);
        }
        finish();
    };

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}

