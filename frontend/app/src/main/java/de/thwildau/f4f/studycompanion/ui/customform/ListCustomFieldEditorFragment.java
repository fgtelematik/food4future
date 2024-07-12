package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;

public class ListCustomFieldEditorFragment extends Fragment {

    private static final String LOG_TAG = "ListCustomFieldEditorActivity";

    public static final String EXTRA_DATA = "EXTRA_DATA"; // default: empty list
    public static final String EXTRA_READONLY = "EXTRA_READONLY"; // default: false
    public static final String EXTRA_LIST_SCHEMA_JSON = "EXTRA_SCHEMA_JSON"; // contains the schema definition of the List field.
            // note that the datatype of the elements must be defined in the "elements_type" field.

    private View rootView = null;

    private TableLayout listTable;
    private ListCustomFieldTableRow lastRow = null;

    private boolean validData = false;
    private boolean readonly;
    private JSONArray data;
    private String label = null;
    private String helpText = null;
    private JSONObject schema;
    private CustomField.FieldType elementType;
    private CustomFieldFactory fieldFactory;


    public ListCustomFieldEditorFragment() {

    }

    public static ListCustomFieldEditorFragment newInstance(Bundle params) {
        ListCustomFieldEditorFragment newFragment = new ListCustomFieldEditorFragment();
        newFragment.setArguments(params);
        return newFragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.custom_field_list_editor, container, false);

        if(!validData) {
            return rootView;
        }

        View buttonAdd = rootView.findViewById(R.id.btnAdd);
        listTable = rootView.findViewById(R.id.valListTable);

        TextView labelTextView = rootView.findViewById(R.id.valListLabel);
        TextView helperTextView = rootView.findViewById(R.id.valListHelp);

        labelTextView.setVisibility(Utils.nullOrEmpty(label) ? View.GONE : View.VISIBLE);
        labelTextView.setText(Utils.nullOrEmpty(label) ? "" : label);

        helperTextView.setVisibility(Utils.nullOrEmpty(helpText) ? View.GONE : View.VISIBLE);
        helperTextView.setText(Utils.nullOrEmpty(helpText)  ? "" : helpText);

        buttonAdd.setOnClickListener(this::onButtonAddClick);

        updateListView();

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Bundle extras = getArguments();

        if(extras == null) {
            return;
        }

        readonly = extras.getBoolean(EXTRA_READONLY, false);
        String dataStr = extras.getString(EXTRA_DATA, "[]");

        String schemaStr = extras.getString(EXTRA_LIST_SCHEMA_JSON, "{}");

        try {
            schema = new JSONObject(schemaStr);
            label = schema.optString("label");
            helpText = schema.optString("helpText");
        } catch (JSONException e) {
            Toast.makeText(getActivity(), "Cannot create view for list elements. Invalid schema format.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        try {
            data = new JSONArray(dataStr);
        } catch(Exception e) {
            Toast.makeText(getActivity(), "Cannot create view for list elements. Invalid data format.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }


        String elementsTypeStr = schema.optString("elements_type");
        try {
            elementType = CustomField.FieldType.valueOf(elementsTypeStr);
        } catch(Exception e) {
            Toast.makeText(getActivity(), "Unknown list element type: '"+elementsTypeStr+"'. List Editor cannot be loaded.", Toast.LENGTH_LONG).show();
            return;
        }

        validData = true;
    }


    private void updateListView() {
        LayoutInflater layoutInflater = getLayoutInflater();

        listTable.removeAllViews();
        fieldFactory = new CustomFieldFactory(this, layoutInflater);

        int numDatasets = data.length();

        if (numDatasets > 0) {
            // List items available
            ListCustomFieldTableRow itemRow = null;

            for (int i = 0; i < numDatasets; i++) {
                String dataStr = getElementDataAsString(data, i);
                itemRow = new ListCustomFieldTableRow(getActivity(), schema, dataStr, readonly, i, this::onButtonDeleteClick, fieldFactory);
                listTable.addView(itemRow);
            }

            lastRow = itemRow;
        }

        rootView.findViewById(R.id.valListNewEnryView).setVisibility(readonly ? View.GONE : View.VISIBLE);
        rootView.findViewById(R.id.valListNoEntries).setVisibility(numDatasets > 0 ? View.GONE : View.VISIBLE);
    }

    private void onButtonDeleteClick(int index) {
        getData();
        data.remove(index);
        updateListView();
    }

    private void addValue(Object value) throws JSONException, NumberFormatException {

        if(value == null || value.toString().isEmpty()) {
            data.put(null);
            return;
        }

        if(elementType == CustomField.FieldType.FloatType) {
            float floatVal = Float.parseFloat(value.toString());
            data.put(floatVal);
        }

        if(elementType == CustomField.FieldType.IntType) {
            float floatVal = Float.parseFloat(value.toString());
            data.put((int)floatVal);
        }

        if(elementType == CustomField.FieldType.BoolType) {
            boolean boolVal = Boolean.parseBoolean(value.toString());
            data.put(boolVal);
        }

        if(elementType == CustomField.FieldType.ADT) {
            JSONObject adtObject = new JSONObject(value.toString());
            data.put(adtObject);
        }

        if(elementType == CustomField.FieldType.FoodList) {
            JSONArray foodlistArray = new JSONArray(value.toString());
            data.put(foodlistArray);
        }

        if(elementType == CustomField.FieldType.StringType
                || elementType == CustomField.FieldType.EnumType
                || elementType == CustomField.FieldType.DateType
                || elementType == CustomField.FieldType.TimeType

        ) {
            data.put(value.toString());
        }
    }

    private String getElementDataAsString(JSONArray elements, int index) {
        Object resObj = "";
        boolean isNull = elements.isNull(index);

        try {
            if (elementType == CustomField.FieldType.FloatType) {
                resObj = isNull ? 0f : (float)elements.getDouble(index);
            }

            if (elementType == CustomField.FieldType.IntType) {
                resObj = isNull ? 0 : elements.getInt(index);
            }

            if (elementType == CustomField.FieldType.BoolType) {
                resObj = isNull ? null : elements.getBoolean(index);
            }

            if (elementType == CustomField.FieldType.ADT) {
                resObj = isNull ? null : elements.getJSONObject(index).toString();
            }

            if (elementType == CustomField.FieldType.FoodList) {
                resObj = isNull ? null : elements.getJSONArray(index).toString();
            }

            if (elementType == CustomField.FieldType.StringType
                    || elementType == CustomField.FieldType.EnumType
                    || elementType == CustomField.FieldType.DateType
                    || elementType == CustomField.FieldType.TimeType
            ) {
                resObj = isNull ? null : elements.getString(index);
            }

            return resObj == null ? null : resObj.toString();

        } catch(JSONException e) {
            return null;
        }
    }


    private void onButtonAddClick(View buttonView) {
        if(readonly) {
            return;
        }

        getData();
        data.put(null);

        updateListView();

        // Open Value Editor of the custom field right after adding a new field (if available)
        lastRow.tryOpenExtraActivity();

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(fieldFactory != null) {
            fieldFactory.handleActivityResultForADTFields(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public JSONArray getData() {
        int numRows = listTable.getChildCount();

        // refill data with modified extracted from the TableRows
        data = new JSONArray();
        try {
            for (int i = 0; i < numRows; i++) {
                ListCustomFieldTableRow row = (ListCustomFieldTableRow) listTable.getChildAt(i);
                addValue(row.getValue());
            }
        }catch (JSONException e) {
            // won't happen
        }

        return data;
    }

    public boolean validateData() {
        int numRows = listTable.getChildCount();
        boolean res = true;

        for (int i = 0; i < numRows; i++) {
            ListCustomFieldTableRow row = (ListCustomFieldTableRow) listTable.getChildAt(i);
            if(Utils.nullOrEmpty(row.getValue())) {
                row.setInvalidValueError();
                res = false;
            }
        }

        return res;
    }
}

