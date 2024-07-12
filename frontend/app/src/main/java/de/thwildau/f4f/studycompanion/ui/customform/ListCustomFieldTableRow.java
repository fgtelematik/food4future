package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import de.thwildau.f4f.studycompanion.R;

public class ListCustomFieldTableRow extends TableRow {

    private final boolean readonly;
    private final String dataStr;
    private final int index;
    private JSONObject elementSchema;
    private final CustomFieldFactory fieldFactory;
    private CustomField dataField;

    private OnDeleteClickListener onDeleteClickListener = null;
    private final Context context;


    public interface OnDeleteClickListener {
        void onDeleteClick(int index);
    }

    public ListCustomFieldTableRow(Context context, JSONObject schema, String elementDataString, boolean readonly, int index, OnDeleteClickListener onDeleteClickListener, CustomFieldFactory fieldFactory) {
        super(context);
        dataStr = elementDataString;
        this.context = context;
        this.readonly = readonly;
        this.index = index;
        this.onDeleteClickListener = onDeleteClickListener;
        this.fieldFactory = fieldFactory;

        // convert list schema to element schema:
        try {
            elementSchema = new JSONObject(schema.toString());
            String elementsType = schema.getString("elements_type");

            elementSchema.put("datatype", elementsType);
            elementSchema.put("maybeNull", false); // list entries must either have a value or may not exist
            elementSchema.remove("elements_type");

            // we do not need Label and HelpText for every individual list item. It's already at the top of the list:
            elementSchema.put("label", "");
            elementSchema.put("helpText", null); // in current Specs, only helpText is nullable

        } catch(JSONException e) {
            // Will not happen
        }
        initListElementView();
    }

    private void initListElementView() {
        inflate(context, R.layout.custom_field_list_entry, this);

        ImageButton buttonDelete = findViewById(R.id.btnDelete);
        buttonDelete.setOnClickListener((v) -> onDeleteClickListener.onDeleteClick(index));
        buttonDelete.setVisibility(readonly ? GONE : VISIBLE);

        TextView fieldText = findViewById(R.id.fieldText);

        removeView(fieldText);

        try {
            dataField = fieldFactory.createCustomFieldFromSchema(null, elementSchema, false);

        } catch (JSONException e) {
            Toast.makeText(getContext(), "Could not create View for list element. Field schema interpretation failed.", Toast.LENGTH_LONG).show();
            return;
        }

        dataField.setValue(dataStr);
        dataField.setEnabled(!readonly);

        View v = dataField.getView();

        TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f);
        v.setLayoutParams(layoutParams);

        addView(v, 0);
    }

    public void setInvalidValueError() {
        dataField.setError(context.getString(R.string.customform_list_entry_invalid));
    }

    public void tryOpenExtraActivity() {
        if(dataField instanceof OpensExtraActivity) {
            ((OpensExtraActivity)dataField).openExtraActivity();
        }
    }

    public Object getValue() {
        return dataField.getValue();
    }

}
