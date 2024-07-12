package de.thwildau.f4f.studycompanion.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.datamodel.StaticResources;
import de.thwildau.f4f.studycompanion.datamodel.StaticResourcesProvider;

public class TextViewActivity extends AppCompatActivity {

    public final static String EXTRA_TEXT_RESOURCE_ID = "EXTRA_TEXT_RESOURCE_ID";
    public final static String EXTRA_TITLE_RESOURCE_ID = "EXTRA_TITLE_RESOURCE_ID";
    public final static String EXTRA_STATIC_RESOURCE_PARCELABLE = "EXTRA_STATIC_RESOURCE_PARCELABLE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_view);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        fillContent();
    }

    private void fillContent() {

        Bundle extras = getIntent().getExtras();

        if (extras == null) {
            return;
        }

        int titleId = extras.getInt(EXTRA_TITLE_RESOURCE_ID);
        if (titleId > 0) {
            getSupportActionBar().setTitle(titleId);
        }


        WebView wv = findViewById(R.id.webView);


        int textId = extras.getInt(EXTRA_TEXT_RESOURCE_ID);
        if (textId > 0) {
            String html = getString(textId);
            wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }

        StaticResources.StaticResource staticResource = extras.getParcelable(EXTRA_STATIC_RESOURCE_PARCELABLE);
        if (staticResource != null) {
            File textResourceFile = StaticResourcesProvider.getStaticResourceFile(this, staticResource);

            if (textResourceFile != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(textResourceFile)));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    String fileExtension = staticResource.getFileExtension();
                    boolean isHtml = "html".equals(fileExtension) || "htm".equals(fileExtension);
                    String lineSeparator = isHtml ? "" : "\n<br />"; // add line breaks for non-html files


                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append(lineSeparator);
                    }
                    reader.close();
                    String textHtml = sb.toString();

                    wv.loadDataWithBaseURL(null, textHtml, isHtml ? "text/html" : "text/plain", "UTF-8", null);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                wv.loadData(getString(R.string.activity_textview_resource_not_found), "text/plain; charset=utf-8", "UTF-8");
            }
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        // Make back arrow return to previous activity
        onBackPressed();
        return true;
    }

}