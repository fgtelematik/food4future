package de.thwildau.f4f.studycompanion.ui.customform;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.enums.Permission;

public class CustomFormFragment extends Fragment {


    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    /* Extras for calling intent */
    public static final String EXTRA_DATA_JSON = "EXTRA_DATA";
    public static final String EXTRA_ADT_ID = "EXTRA_ADT_ID";
    public static final String EXTRA_INFOTEXT = "EXTRA_INFOTEXT";
    public static final String EXTRA_BUTTON_CONFIRM = "EXTRA_BUTTON_CONFIRM";

    /* Extras for resulting Intent */
    public static final String EXTRA_UPDATED_DATA_JSON = "EXTRA_UPDATED_DATA_JSON";

    private static final String DATA_CURRENT_INPUT = "DATA_CURRENT_INPUT";

    private List<CustomField> fieldList = new ArrayList<>();
    private Map<CustomField, Object> initialValue = new HashMap<>();

    private JSONObject initFieldData;
    private  CustomFieldFactory fieldFactory;

    private Bundle parameters;
    private View rootView;
    private TextView mainDescriptionTextView;
    private Utils.FragmentWrapperActivityCallback customFormCallback;

    private Integer currentOffsetDay = null;

    private String restoredInputData = null;

    public CustomFormFragment() {
        // Required empty public constructor
    }

    public void setCustomFormCallback(Utils.FragmentWrapperActivityCallback callback) {
        customFormCallback = callback;
    }

    public static CustomFormFragment newInstance(Bundle params, Utils.FragmentWrapperActivityCallback callback) {
        CustomFormFragment fragment = new CustomFormFragment();
        fragment.setArguments(params);
        fragment.setCustomFormCallback(callback);
        return fragment;
    }

    private static void putFieldValue(JSONObject dataObject, String fieldId, Object data, CustomField.FieldType type) throws JSONException, NumberFormatException {

        if(data == null) {
            dataObject.put(fieldId, JSONObject.NULL);
            return;
        }

        if(data.toString().isEmpty()) {
            dataObject.put(fieldId, "");
            return;
        }

        if(type == CustomField.FieldType.FloatType) {
            float floatVal = Float.parseFloat(data.toString());
            dataObject.put(fieldId, floatVal);
        }

        if(type == CustomField.FieldType.IntType) {
            float floatVal = Float.parseFloat(data.toString());
            dataObject.put(fieldId, (int)floatVal);
        }

        if(type == CustomField.FieldType.BoolType) {
            boolean boolVal = Boolean.parseBoolean(data.toString());
            dataObject.put(fieldId, boolVal);
        }

        if(type == CustomField.FieldType.ADT) {
            JSONObject adtObject = new JSONObject(data.toString());
            dataObject.put(fieldId, adtObject);
        }

        if(type == CustomField.FieldType.FoodList || type == CustomField.FieldType.ListType) {
            JSONArray listArray = new JSONArray(data.toString());
            dataObject.put(fieldId, listArray);
        }

        if(type == CustomField.FieldType.StringType
                || type == CustomField.FieldType.EnumType
                || type == CustomField.FieldType.DateType
                || type == CustomField.FieldType.TimeType

        ) {
            dataObject.put(fieldId, data.toString());
        }
    }

    private Object getFieldValue(String id, JSONObject fieldData, JSONObject schema) {
        Object res = null;
        boolean useDefaultValue = false;

        JSONObject dataSource = fieldData;
        String fieldId = id;

        if(!dataSource.has(fieldId)) {
            // field does not exist in dataset

            if(schema.has("defaultValue")) {
                // Default value field available in schema. So read data from this field.
                dataSource = schema;
                fieldId = "defaultValue";
            } else {
                return null;
            }
        }

        if(dataSource.isNull(fieldId)) {
            // field or default value is null, so return null
            return null;
        }

        try {
            CustomField.FieldType type = CustomField.FieldType.valueOf(schema.getString("datatype"));
            if (type == CustomField.FieldType.FloatType) {
                res = (float) dataSource.getDouble(fieldId);
            }

            if (type == CustomField.FieldType.IntType) {
                res = dataSource.getInt(fieldId);
            }

            if (type == CustomField.FieldType.BoolType) {
                res = dataSource.getBoolean(fieldId);
            }

            if (type == CustomField.FieldType.ADT) {
                res = dataSource.getJSONObject(fieldId).toString();
            }

            if (type == CustomField.FieldType.FoodList || type == CustomField.FieldType.ListType) {
                res = dataSource.getJSONArray(fieldId).toString();
            }

            if (type == CustomField.FieldType.StringType
                    || type == CustomField.FieldType.EnumType
                    || type == CustomField.FieldType.DateType
                    || type == CustomField.FieldType.TimeType
            ) {
                res = dataSource.getString(fieldId);
            }

            return res;
        } catch(JSONException e) {
            return null;
        }

    }

    /**
     * Create CustomField instances and place all views for the form fields specified in
     * both activity extras EXTRA_ADT_ID (a passed ADT definition) and
     * EXTRA_DATA_JSON (a passed dataset). Passing only one of the extras is sufficient.
     * If both are passed, the unification of fields defined in both parts is displayed.
     *
     */
    private void createCustomForm(LayoutInflater layoutInflater) {
        ViewGroup fieldsView = rootView.findViewById(R.id.fieldsView);
        fieldsView.removeAllViews();

        fieldFactory = new CustomFieldFactory(this, layoutInflater);
        initFieldData = new JSONObject();

        String adtID = parameters.getString(CustomFormFragment.EXTRA_ADT_ID);

        String formDataString;

        // Obtain form content either von passed data string or from cached state (e.g. on screen rotation)
        formDataString = restoredInputData == null ? parameters.getString(CustomFormFragment.EXTRA_DATA_JSON) : restoredInputData;

        List<String> adtFields = new ArrayList<>(); // fields acquired from specified ADT definition (if available)
        List<String> datasetFields = new ArrayList<>();  // fields acquired from specified dataset (if available)

        if(adtID != null) {
            // Activity has EXTRA_ADT_ID
            adtFields = SchemaProvider.getADTFields(adtID);

            if(adtFields == null) {
                mainDescriptionTextView.setVisibility(View.VISIBLE);
                mainDescriptionTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.design_default_color_error));
                mainDescriptionTextView.setText(getString(R.string.customform_invalid_schema));
                adtFields = new ArrayList<>();
            }
        }

        if(formDataString != null) {
            // Activity has EXTRA_DATA_JSON
            try {
                initFieldData = new JSONObject(formDataString);
                Iterator<String> keys = initFieldData.keys();
                while(keys.hasNext()) {
                    datasetFields.add(keys.next());
                }

                currentOffsetDay = null;
                try {
                    // extract effective day from field data, if available
                    JSONObject effectiveDaySchema = SchemaProvider.getSchemaForField("effective_day");

                    String effectiveDayStr = (String) getFieldValue("effective_day", initFieldData, effectiveDaySchema);
                    if (effectiveDayStr != null) {
                        // effective_day field available in current dataset (should be the case for daily questions)

                        Date effectiveDay = Utils.getServerTimeFormat().parse(effectiveDayStr);
                        currentOffsetDay = Utils.determineCurrentOffsetDay(effectiveDay);
                    }
                } catch(Throwable t) {
                    //
                }


            } catch (JSONException e) {
                e.printStackTrace();
            }


        }

        // old: generate combined field set from ADT and existing dataset.
//        Set<String> combinedSet = new LinkedHashSet<>(adtFields);
//        combinedSet.addAll(datasetFields);
// WE DO NOT DO THAT ANYMORE: Fields not existing in ADT will not be displayed because: why should they?

        Iterator<String> keys = adtFields.iterator();

        try {
            boolean firstElement = true;
            CustomFormFieldContainer currentContainer = null;

            // Create and add form field for each data
            while(keys.hasNext()) {

                String id = keys.next();
                JSONObject schemaObj = SchemaProvider.getSchemaForField(id);

                if(schemaObj == null) {
                    // Field Schema not found. Should not happen.
                    BackendIO.serverLog(Log.ERROR, "CustomFormFragment","ERROR on building custom form! Schema for field id '" + id + "' not found.");
                    continue;
                }

                String fieldTypeStr = schemaObj.optString("datatype");
                boolean isContainer = CustomField.FieldType.Container.name().equals(fieldTypeStr);

                if(!SchemaProvider.checkDisplayConstraint(currentOffsetDay, schemaObj)) {
                    // Field has a display constraint set by "displayPeriodDays", which does it
                    // not allow to be displayed for the "effective_day" specified in initial dataset.
                    continue;
                }

                boolean canEdit = SchemaProvider.checkPermissionForField(schemaObj, Permission.Edit);
                boolean canRead = canEdit || SchemaProvider.checkPermissionForField(schemaObj, Permission.Read);

                if(!canRead) {
                    // User is not allowed to see this field.
                    continue;
                }

                if (currentContainer == null && !isContainer) {
                    // create default container for first fields
                    currentContainer = new CustomFormFieldContainer(getLayoutInflater(), fieldsView);
                    fieldsView.addView(currentContainer.getContainerView());

                    firstElement = true;
                } else if(isContainer) {
                    // replace current by new container
                    String containerTitle = schemaObj.optString("label");
                    String containerHelpText = schemaObj.optString("helpText");

                    currentContainer = new CustomFormFieldContainer(getLayoutInflater(), fieldsView);
                    currentContainer.setTitle(containerTitle);
                    currentContainer.setHelpText(containerHelpText);

                    fieldsView.addView(currentContainer.getContainerView());

                    firstElement = true;
                    continue;
                }


                if(!firstElement) {
                    // Draw field separator
                    View separatorView = getLayoutInflater().inflate(R.layout.custom_field_separator, currentContainer.getContainerView(), false);
                    currentContainer.addFieldView(separatorView);
                }

                firstElement = false;

                // isNew is true when current field is part of ADT definition, but not part of the dataset.
                // This would happen, when new fields where added to ADT definition after data already existed
                boolean isNew = !datasetFields.isEmpty() && !datasetFields.contains(id);

                CustomField field = fieldFactory.createCustomFieldFromSchema(id, schemaObj, isNew);


                if(field != null) {
                    Object value = getFieldValue(id, initFieldData, schemaObj);

                    if(value != null) {
                        field.setValue(value); // apply passed value to field element
                    }

                    if(!canEdit) {
                        // User is not allowed to edit field data
                        field.setEnabled(false);
                    }

                    fieldList.add(field);
                    initialValue.put(field, value);

                    currentContainer.addFieldView(field.getView());
                }
            }

            if(fieldList.isEmpty()) {
                // No form fields were built at all.
                Toast.makeText(getActivity(), R.string.customform_norights, Toast.LENGTH_LONG).show();
            }
        } catch(JSONException e) {
            Toast.makeText(getActivity(), R.string.customform_error_parsing, Toast.LENGTH_LONG).show();
            return;
        }
    }

    private JSONObject buildResultDataObject() {

        JSONObject res = initFieldData; // using the dataset passed on fragment creation as base (initialized in createCustomForm(), might be empty):
        // more elegant would be using a deep copy here, but there's no efficient way on Android to
        // do that: https://stackoverflow.com/questions/12809779/how-do-i-clone-an-org-json-jsonobject-in-java
        // Instead we're garbling the initFieldData Object directly, since we don't need it anymore anyways

        try {
            for (CustomField field : fieldList) {
                putFieldValue(res, field.getID(), field.getValue(), field.getType());
            }
        } catch (JSONException | NumberFormatException e) {
            Toast.makeText(getActivity(), R.string.customform_error_buildresult, Toast.LENGTH_LONG ).show();
            return null;
        }

        return res;
    }

    private boolean verifyValues() {
        boolean res = true;
        for(CustomField field : fieldList) {
            if(!field.verifyValue()) {
                field.setError(getString(R.string.invalid_value_error_msg));
                res = false;
            }
        }
        return res;
    }

    private void confirmButtonPressed(View button) {
        boolean valid = verifyValues();

        if(!valid) {
            Toast.makeText(getActivity(), R.string.customform_request_correction, Toast.LENGTH_LONG).show();
            return;
        }

        JSONObject resultDataArray = buildResultDataObject();

        if(resultDataArray == null) {
            return;
        }

        customFormCallback.onCustomFormResult(false, resultDataArray.toString());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        fieldFactory.handleActivityResultForADTFields(requestCode, resultCode, data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parameters = getArguments();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_custom_form, container, false);

        // Configure OK Button, if available
        String buttonText = parameters.getString(CustomFormFragment.EXTRA_BUTTON_CONFIRM);
        Button btnConfirm = rootView.findViewById(R.id.btnConfirm);
        if(buttonText == null) {
            btnConfirm.setVisibility(View.GONE);
        } else {
            btnConfirm.setText(buttonText);
            btnConfirm.setOnClickListener(this::confirmButtonPressed);
        }

        // Configure Main description text header, if available
        String mainDescription = parameters.getString(CustomFormFragment.EXTRA_INFOTEXT);
        mainDescriptionTextView = rootView.findViewById(R.id.mainDescription);
        if(mainDescription == null) {
            mainDescriptionTextView.setVisibility(View.GONE);
        } else {
            mainDescriptionTextView.setText(mainDescription);
        }

        restoredInputData = null;
        if(savedInstanceState != null && savedInstanceState.containsKey(DATA_CURRENT_INPUT)) {
            restoredInputData = savedInstanceState.getString(DATA_CURRENT_INPUT);
        }

        FragmentActivity activity = getActivity();
        if(activity != null) {
            activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackPressed();
                }
            });
        }

        // Create Form
        createCustomForm(inflater);

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save current input state
        JSONObject currentInputData = buildResultDataObject();
        if(currentInputData == null) {
            return;
        }

        outState.putString(DATA_CURRENT_INPUT, currentInputData.toString());
    }

    public void handleBackPressed() {

        // Check if values have changed
        boolean changed = false;
        for(CustomField field : fieldList) {
            Object newValue = field.getValue();
            Object initValue = initialValue.get(field);

            if(Utils.nullOrEmpty(newValue)  && Utils.nullOrEmpty(initValue)) {
                continue;
            }

            if(Utils.nullOrEmpty(newValue)  || Utils.nullOrEmpty(initValue)) {
                // acts as XOR, since AND case is already excluded above
                // and XOR always shows difference
                changed = true;
                break;
            }


            // last case: both values not null
            if(!newValue.toString().equals(initValue.toString())) {
                changed = true;
                break;
            }
        }

        if(changed) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.dialog_msg_discard)
                    .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                        customFormCallback.onCustomFormResult(true, null);
                    })
                    .setCancelable(true)
                    .setNegativeButton(R.string.no, null)
                    .create();
            dialog.show();
        } else {
            customFormCallback.onCustomFormResult(true, null);
        }

    }


}