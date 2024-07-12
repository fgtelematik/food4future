package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;

public class ListCustomField extends CustomField implements OpensExtraActivity {

    private final Fragment baseFragment;
    private final TextView errorText;
    private final TextView summaryText;
    private final ImageButton editButton;
    private final View fieldView;

    private boolean enabled;

    private String listSchema = null;

    private String dataStr;

    private int requestCode;

    public ListCustomField(String id, String label, String helpText, Fragment baseFragment) {
        super(id, FieldType.ListType);
        this.baseFragment = baseFragment;
        enabled = true;

        fieldView = baseFragment.getLayoutInflater().inflate(R.layout.custom_field_list, null);

        Utils.setTextOrHideTextView(fieldView.findViewById(R.id.valListLabel), label);
        Utils.setTextOrHideTextView(fieldView.findViewById(R.id.valListHelp), helpText);

        errorText = fieldView.findViewById(R.id.valListError);
        editButton = fieldView.findViewById(R.id.valListBtnEdit);
        editButton.setOnClickListener(this::onButtonEditClick);

        summaryText = fieldView.findViewById(R.id.valListEntriesSummary);

        JSONObject schema = SchemaProvider.getSchemaForField(id);

        if(schema != null) {
            listSchema = schema.toString();
        } else {
            setError(baseFragment.getString(R.string.customform_list_invalid_schema));
            editButton.setEnabled(false);
        }

        dataStr = "[]";
        updateSummary(new JSONArray());

        requestCode = getNextRequestCode();
    }

    private void updateSummary(JSONArray dataJson) {
        int count = dataJson.length();
        String summaryStr;
        if(count == 1) {
            summaryStr = baseFragment.getString(R.string.customform_list_summary_one_entry);
        } else {
            summaryStr = baseFragment.getString(R.string.customform_list_summary, count);
        }
        summaryText.setText(summaryStr);
    }

    private void updateUiForEnabledState() {
        if(listSchema == null && enabled) {
            return;
        }

        boolean buttonState = true;

        if(!enabled) {
            buttonState = !Utils.nullOrEmpty(dataStr) && !dataStr.equals("[]");
        }

        editButton.setEnabled(buttonState);
    }

    @Override
    public Object getValue() {
        return dataStr;
    }

    @Override
    public void setValue(Object value) {
        JSONArray dataJson;

        if (Utils.nullOrEmpty(value)) {
            dataStr = "[]";
        } else {
            dataStr = value.toString();
        }

        if(value instanceof JSONArray) {
            dataJson = (JSONArray) value;
        } else {
            try {
                dataJson = new JSONArray(dataStr);
            } catch (JSONException e) {
                setError(baseFragment.getString(R.string.customform_list_invalid_format));
                dataStr = "[]";
                dataJson = new JSONArray();
            }
        }

        updateSummary(dataJson);
    }

    @Override
    public View getView() {
        return fieldView;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if(listSchema == null && enabled) {
            return;
        }

        this.enabled = enabled;

        updateUiForEnabledState();
    }

    @Override
    public void openExtraActivity() {
        onButtonEditClick(null);
    }

    private void onButtonEditClick(View button) {

        Intent i = new Intent(baseFragment.getContext(), ListCustomFieldEditorActivity.class);
        i.putExtra(ListCustomFieldEditorFragment.EXTRA_DATA, dataStr);
        i.putExtra(ListCustomFieldEditorFragment.EXTRA_LIST_SCHEMA_JSON, listSchema);
        i.putExtra(ListCustomFieldEditorFragment.EXTRA_READONLY, !enabled);

        baseFragment.startActivityForResult(i, requestCode);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }



    @Override
    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.handleActivityResult(requestCode, resultCode, data);

        if(requestCode == this.requestCode && data!=null) {
            String updatedListData = data.getStringExtra(ListCustomFieldEditorFragment.EXTRA_DATA);
            if(!updatedListData.equals(dataStr)) {
                setValue(updatedListData);
                setError(null);
            }
        }
    }
}
