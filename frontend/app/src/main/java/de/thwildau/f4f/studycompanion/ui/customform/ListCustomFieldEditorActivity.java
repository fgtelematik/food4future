package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import de.thwildau.f4f.studycompanion.R;

public class ListCustomFieldEditorActivity extends AppCompatActivity {

    private ListCustomFieldEditorFragment customFormFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        // Configure Toolbar, set Back Button and Title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        String title = generateTitle();

        getSupportActionBar().setTitle(title != null ? title : getString(R.string.generic_custom_form_title));


        // create and include ListCustomFieldEditorFragment
        customFormFragment = ListCustomFieldEditorFragment.newInstance(getIntent().getExtras());
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, customFormFragment)
                .commit();
    }

    private String generateTitle() {
        String title = getIntent().getStringExtra(CustomFormFragment.EXTRA_TITLE);
        if(title != null) {
            return title;
        }

        String label = "";
        try {
            JSONObject schema = new JSONObject(getIntent().getStringExtra(ListCustomFieldEditorFragment.EXTRA_LIST_SCHEMA_JSON));
            label = schema.getString("label");
        } catch(Exception e) {
            label = "n/a";
        }

        return getString(R.string.customform_list_editor_title, label);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return tryReturn();
    }

    @Override
    public void onBackPressed() {
        tryReturn();
    }

    private boolean tryReturn() {
        if(customFormFragment.validateData()) {
            Intent i = new Intent();
            i.putExtra(ListCustomFieldEditorFragment.EXTRA_DATA, customFormFragment.getData().toString());
            setResult(RESULT_OK, i);
            finish();
            return true;
        }
        return false;
    }
}

