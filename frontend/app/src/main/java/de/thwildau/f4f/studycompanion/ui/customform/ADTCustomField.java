package de.thwildau.f4f.studycompanion.ui.customform;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.EnumerationElement;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;

public class ADTCustomField extends CustomField implements OpensExtraActivity {

    private int requestCode;

    private View fieldView;
    private TextView textHelper;
    private int textHelperColor;
    private TextView textLabel;
    private Button buttonEdit;
    private Button buttonClear;
    private ViewGroup summaryContainer;
    private TextView summaryText;

    private String helperText;
    private String label;

    private boolean enabled = true;
    private Fragment baseFragment;

    private String fieldData = null;


    public ADTCustomField(String id, String adtId, String label, String helperText, Fragment baseFragment) {
        super(id, FieldType.ADT);
        setAdtOrEnumID(adtId);

        requestCode = getNextRequestCode();

        this.baseFragment = baseFragment;
        this.helperText = helperText;
        this.label = label;

        fieldView = baseFragment.getLayoutInflater().inflate(R.layout.custom_field_adt, null);
        textHelper = fieldView.findViewById(R.id.valADTHelper);
        textLabel = fieldView.findViewById(R.id.valADTLabel);
        buttonEdit = fieldView.findViewById(R.id.valADTbtnEdit);
        summaryContainer = fieldView.findViewById(R.id.valADTSummaryContainer);
        summaryText = fieldView.findViewById(R.id.valADTsummaryText);

        buttonClear = fieldView.findViewById(R.id.valADTbtnClear);
        buttonClear.setVisibility(View.GONE);

        Utils.setTextOrHideTextView(textLabel, label);
        Utils.setTextOrHideTextView(textHelper, helperText);

        textHelperColor = textHelper.getTextColors().getDefaultColor();

        buttonEdit.setOnClickListener(this::onButtonEditClick);
        buttonClear.setOnClickListener(this::onButtonClearClick);

        setValue(null);
    }

    private void updateSummary(Object value) {
        JSONObject valJson;
        StringBuilder resStr = null;

        if(value == null) {
            return;
        }

        if(!Utils.nullOrEmpty(helperText)) {
            // show summary only when no helper text is set!
            summaryContainer.setVisibility(View.GONE);
            return;
        }

        summaryContainer.setVisibility(View.VISIBLE);



        if(value instanceof JSONObject) {
            valJson = (JSONObject)value;
        } else {
            try {
                valJson = new JSONObject(value.toString());
            } catch (JSONException e) {
                return;
            }
        }

        // try to extract values in their proper formats
        Iterator<String> it = valJson.keys();
        while(it.hasNext()) {
            String key = it.next();
            String valStr = null;

            // Ignore fields with null or empty strings
            if(valJson.isNull(key)) {
                continue;
            }

            try {
                valStr = valJson.getString(key);
                if(Utils.nullOrEmpty(valStr)) {
                    continue;
                }
            } catch(Exception e) { /* ignore */ }

            if(resStr == null) {
                resStr = new StringBuilder();
            } else {
                resStr.append("\n");
            }

            String keyStr = key;
            JSONObject fieldSchema = SchemaProvider.getSchemaForField(key);
            if(fieldSchema != null && fieldSchema.has("label")) {
                keyStr = fieldSchema.optString("label");
            }

            resStr.append(keyStr).append(": ");

            // Try String
            if(valStr != null) {
                // Try Date or Time
                String dateTimeStr = Utils.tryConvertReadableDateOrTime(valStr);
                if(dateTimeStr != null) {
                    valStr = dateTimeStr;
                }

                // Try Enum Value (if field is enum, replace the element identifier by its label)
                if(fieldSchema != null && fieldSchema.optString("datatype").equals("EnumType")) {
                    List<EnumerationElement> enumElements = SchemaProvider.getEnumElements(fieldSchema.optString("adt_enum_id"));
                    if(enumElements != null) {
                        for (EnumerationElement el : enumElements) {
                            if(valStr.equals(el.getElementId())) {
                                valStr = el.getLabel();
                                break;
                            }
                        }
                    }
                }

                resStr.append(valStr);
                continue;
            }

            // Try Boolean
            try {
                resStr.append(valJson.getBoolean(key));
                continue;
            } catch (JSONException e) { /* ignore */ }

            // Try Double
            try {
                resStr.append(valJson.getDouble(key));
                continue;
            } catch (JSONException e) { /* ignore */ }

            // Try Integer
            try {
                resStr.append(valJson.getInt(key));
                continue;
            } catch (JSONException e) { /* ignore */ }

            // Try Long
            try {
                resStr.append(valJson.getLong(key));
                continue;
            } catch (JSONException e) { /* ignore */ }

            // Try List
            try {
                valJson.getJSONArray(key);
                resStr.append(StudyCompanion.getAppContext().getString(R.string.adt_placeholder_list));
                continue;
            } catch (JSONException e) { /* ignore */ }

            // Try ADT
            try {
                valJson.getJSONObject(key);
                resStr.append(StudyCompanion.getAppContext().getString(R.string.adt_placeholder_adt));
            } catch (JSONException e) { /* ignore */ }
        }

        summaryText.setText(resStr != null ? resStr.toString() : "");

    }

    @Override
    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == this.requestCode && resultCode == Activity.RESULT_OK) {
            setValue(data.getExtras().getString(CustomFormFragment.EXTRA_UPDATED_DATA_JSON));
            setError(null);
        }
    }

    @Override
    public void openExtraActivity() {
        onButtonEditClick(null);
    }

    private void onButtonEditClick(View v) {
        if(!enabled) {
            return;
        }

        Intent i = new Intent(baseFragment.getContext(), CustomFormActivity.class);
        i.putExtra(CustomFormFragment.EXTRA_BUTTON_CONFIRM, baseFragment.getString(R.string.save_data));

        if(fieldData != null) {
            i.putExtra(CustomFormFragment.EXTRA_DATA_JSON, fieldData.toString());
        }
        
        i.putExtra(CustomFormFragment.EXTRA_ADT_ID, getAdtOrEnumID());
        i.putExtra(CustomFormFragment.EXTRA_TITLE, label);

        baseFragment.startActivityForResult(i, requestCode);
    }

    private void onButtonClearClick(View v) {

        AlertDialog dialog = new AlertDialog.Builder(baseFragment.getActivity())
                .setMessage(baseFragment.getString(R.string.dialog_msg_clear_adt, label))
                .setCancelable(true)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                    setValue(null);
                } ).create();

        dialog.show();
    }

    @Override
    public void setMaybeNull(boolean maybeNull) {
        super.setMaybeNull(maybeNull);
        buttonClear.setVisibility(maybeNull ? View.VISIBLE : View.GONE);
    }

    @Override
    public Object getValue() {
        return fieldData;
    }

    @Override
    public void setValue(Object value) {
        if(value == null) {
            fieldData = null;
            buttonClear.setEnabled(false);
            buttonEdit.setText(R.string.button_create);
        } else {
            buttonEdit.setText(R.string.button_edit);
            fieldData = value.toString();
            buttonClear.setEnabled(enabled);
        }

        updateSummary(value);
    }

    @Override
    public View getView() {
        return fieldView;
    }

    @Override
    public void setEnabled(boolean enabled) {
        buttonEdit.setEnabled(enabled);
        buttonClear.setEnabled(fieldData != null ? enabled : false);
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setError(String errorMessage) {
        super.setError(errorMessage);
        if(errorMessage == null) {
            if(helperText == null) {
                textHelper.setVisibility(View.GONE);
            } else {
                textHelper.setText(helperText);
                textHelper.setTextColor(textHelperColor);
            }
        } else {
            textHelper.setVisibility(View.VISIBLE);
            textHelper.setText(errorMessage);
            textHelper.setTextColor(ContextCompat.getColor(baseFragment.getContext(), R.color.design_default_color_error));
        }
    }
}
