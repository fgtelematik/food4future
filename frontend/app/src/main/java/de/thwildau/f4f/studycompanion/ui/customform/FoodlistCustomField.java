package de.thwildau.f4f.studycompanion.ui.customform;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.EnumerationElement;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.ui.EnumSequenceController;

public class FoodlistCustomField extends CustomField {
//    private enum CreationStage {
//        A_MainTypeInput, // Hauptnahrungsart eingeben, z.B. Obst, Gemüse, Convenience...
//        B_SubTypeInput, // Untergruppe angeben, z.B. Apfel, Gurke, Ketchup,...
//        C_StateInput, // Verarbeitungstyp eingegeben (falls erforderlich), z.B. roh, gegart...
//        D_AmountInput, // Menge angeben
//        E_TimeInput, // Zeit des Nahrungskonsums eingeben
//        IDLE // no current data input process
//    }

    private static final String LOG_TAG = "FoodlistCustomField";
    private static final String INITIAL_ENUM_ID = "FoodType"; // First enum to show after "+"-Button was pressed
    private static final String FOOD_ITEM_LABEL_SUMMARY_PLACEHOLDER = "FoodItemLabel";

    // if hideNoConsumptionsCheckboxBeforeEvening is set, the "No Consumptions Checkbox today"
    // is never displayed before this hour of day (24h) in local device time.
    private static final int HOUR_WHEN_EVENING_BEGINS = 18;

    private JSONArray fieldData;
    private Fragment baseFragment;
    private View fieldView = null;
    private boolean hideDescription = false;

    private List<View> removeEntryButtons;
    private View addButton;
    private CheckBox noConsumptionCheckbox;

    private boolean enabled = true;
    private boolean hideNoConsumptionsCheckboxBeforeEvening = false;
    private boolean addButtonEnabled = true;

    private final EnumSequenceController enumSequenceController;
    private Utils.ObservableValue<String> observableFieldData = null;


    public FoodlistCustomField(String id, FieldType fieldType, Fragment baseFragment) {
        super(id, fieldType);
        this.baseFragment = baseFragment;

        enumSequenceController = new EnumSequenceController(baseFragment, getNextRequestCode());
        enumSequenceController.getObservableResultDataset().addObserver((object, newEntry) -> addEntry(newEntry));

        updateView();
    }

    public void setHideDescription(boolean hideDescription) {
        this.hideDescription = hideDescription;

        updateView();
    }

    private void updateView() {
        removeEntryButtons = new ArrayList<>();

        LayoutInflater layoutInflater;
        try {
            layoutInflater = baseFragment.getLayoutInflater();
        } catch(Throwable e) {
            return;
        }

        if (fieldView == null) {
            fieldView = layoutInflater.inflate(R.layout.custom_field_foodlist, null);
        }
        ViewGroup container = fieldView.findViewById(R.id.valFoodlistContainer);

        TextView labelText = fieldView.findViewById(R.id.valFoodLabel);
        TextView helperText = fieldView.findViewById(R.id.valFoodHelperText);

        labelText.setVisibility(hideDescription ? View.GONE : View.VISIBLE);
        helperText.setVisibility(hideDescription ? View.GONE : View.VISIBLE);

        labelText.setText(R.string.foodlist_title);
        helperText.setText(R.string.foodlist_helpertext);

        container.removeAllViews();

        if (fieldData != null && fieldData.length() > 0) {
            // Food consumption entries available
            int numDatasets = fieldData.length();
            for (int i = 0; i < numDatasets; i++) {
                View elementView = baseFragment.getLayoutInflater().inflate(R.layout.custom_field_foodlist_entry, null);
                TextView entryText = elementView.findViewById(R.id.valFoodEntryText);
                TextView timeText = elementView.findViewById(R.id.valFoodEntryTime);

                View removeButton = elementView.findViewById(R.id.valFoodBtnRemove);
                removeButton.setEnabled(isEnabled());
                removeButton.setOnClickListener(this::onRemoveButtonPress);

                try {
                    JSONObject entryObject = fieldData.getJSONObject(i);
                    String entrySummary = generateSummary(entryObject);
                    entryText.setText(entrySummary);

                } catch (Exception e) {
                    entryText.setText("Entry " + (i + 1));
                }

                timeText.setText(""); // We might display time of consumption in future...

                container.addView(elementView);
                removeEntryButtons.add(removeButton);

            }
        }

        View addElementView = baseFragment.getLayoutInflater().inflate(R.layout.custom_field_foodlist_entry_add, null);
        addButton = addElementView.findViewById(R.id.valFoodBtnAdd);
        addButton.setOnClickListener(this::onAddButtonPress);
        addElementView.findViewById(R.id.valFoodAddText).setOnClickListener(this::onAddButtonPress);

        setAddButtonEnabled(addButtonEnabled);
        container.addView(addElementView);

        if(noConsumptionCheckbox == null) {
            noConsumptionCheckbox = fieldView.findViewById(R.id.valFoodCheckNoConsumption);
            noConsumptionCheckbox.setOnCheckedChangeListener(this::onNoConsumptionCheckboxChanged);
        }
        noConsumptionCheckbox.setVisibility(noConsumptionCheckbox.isChecked() || isFoodlistNullOrEmpty() && !checkBeforeEvening() ? View.VISIBLE : View.GONE);
    }

    /**
     * Analyse the current food entry object and extract food amount, food item and variant information
     * to generate a summary string used to be displayed in the food consumptions list.
     * @param entryObject The food consumption object
     * @return a string which summarizes the consumption in human-understandable format
     */
    private static String generateSummary(JSONObject entryObject) {

        String amountFieldId = null;
        String foodFieldId = null;
        String variantFieldId = null;

        // STEP 1: extract relevant field ids
        for (Iterator<String> it = entryObject.keys(); it.hasNext(); ) {
            String fieldId = it.next();

            if (fieldId.toLowerCase().contains("amount")) {
                // use the last amount field found
                amountFieldId = fieldId;
                continue;
            }

            SchemaProvider.EnumMetadata enumMetadata = SchemaProvider.getEnumMetadata(fieldId);
            // fieldId might not be a valid enum id, but in this case
            // an EnumMetadata instance is returned with all fields set to null/false

            if (enumMetadata.containsFoodItems()) {
                foodFieldId = fieldId;
            } else if(
                    foodFieldId != null &&
                            !fieldId.toLowerCase().contains("amount") &&
                            !fieldId.equals("food_id") &&
                            !fieldId.toLowerCase().contains("barcode")
            ){
                // the last field which comes AFTER the food field id, and is not considered being an amount specification
                // is considered variant (such as Vegetable State, etc.)
                variantFieldId = fieldId;
            }
        }

        StringBuilder summaryBuilder = new StringBuilder();

        // STEP 2: Try to extract food amount
        if(amountFieldId != null) {
            String amountStr = entryObject.optString(amountFieldId);
            summaryBuilder.append(amountStr).append(" ");

            if(Utils.isNumeric(amountStr)) {
                // Add unit to pure number (if specified)
                String fieldLabel = SchemaProvider.getEnumMetadata(amountFieldId).getLabel();

                // Special (hard-coded) case: pieces -> Use "x" symbol
                if(amountFieldId.toLowerCase().contains("pieces"))
                    fieldLabel="x";

                if(!Utils.nullOrEmpty(fieldLabel)) {
                    summaryBuilder.append(fieldLabel).append(" ");
                }
            }
        }

        // STEP 3: Try to extract food label
        if (foodFieldId == null) {
            // Placeholder for food item, if no enum containing food items was found
            summaryBuilder.append(StudyCompanion.getAppContext().getResources().getString(R.string.enum_missing_label)).append(" ");
        } else { // foodFieldId != null
            String foodItemStr = entryObject.optString(foodFieldId);

            if (!foodItemStr.isEmpty()) {
                // Obtain label for selected element from enum schemas
                List<EnumerationElement> enumElements = SchemaProvider.getEnumElements(foodFieldId);
                if (enumElements != null && !foodFieldId.toLowerCase().contains("amount")) {
                    for (EnumerationElement enumElement : enumElements) {
                        if (enumElement.getElementId().equals(foodItemStr)) {
                            String elementTitle = enumElement.getSummaryLabel();
                            if (foodItemStr.equalsIgnoreCase("label")) {
                                // SPECIAL CASE: selected enum element equals "label"
                                // in this case, we substitute element by the user-entered custom product label
                                foodItemStr = entryObject.optString("Label");
                            } else if (!Utils.nullOrEmpty(elementTitle)) {
                                foodItemStr = enumElement.getSummaryLabel();
                                foodItemStr = foodItemStr.replace("\n", "").replace("\r", "");
                            }
                        }
                    }
                }
            }

            summaryBuilder.append(foodItemStr);
        }

        // STEP 3: Try to extract optional variant
        if(variantFieldId != null) {
            String variantStr = entryObject.optString(variantFieldId);

            List<EnumerationElement> enumElements = SchemaProvider.getEnumElements(variantFieldId);
            if(enumElements != null) {
                for (EnumerationElement enumElement : enumElements) {
                    if (enumElement.getElementId().equals(variantStr)) {
                        variantStr = enumElement.getSummaryLabel();
                    }
                }
            }

            if(!Utils.nullOrEmpty(variantStr)) {
                summaryBuilder.append(" (").append(variantStr).append(")");
            }
        }

        return summaryBuilder.toString();
    }


    private void onRemoveButtonPress(View buttonView) {
        int index = removeEntryButtons.indexOf(buttonView);
        fieldData.remove(index);
        updateView();
        updateObservableFieldData();
    }

    private void onAddButtonPress(View buttonView) {
        if(noConsumptionCheckbox.isChecked()) {
            return;
        }
        enumSequenceController.startSequence(INITIAL_ENUM_ID);
    }

    private void onNoConsumptionCheckboxChanged(CompoundButton buttonView, boolean isChecked) {
        setAddButtonEnabled(!isChecked);
        updateObservableFieldData();
    }

    private boolean isFoodlistNullOrEmpty() {
        return fieldData == null || fieldData.length() == 0;
    }

    private boolean checkBeforeEvening() {
        if(!hideNoConsumptionsCheckboxBeforeEvening) {
            // always false if flag is unset
            return false;
        }
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) < HOUR_WHEN_EVENING_BEGINS;
    }

    @Override
    public Object getValue() {

        if (isFoodlistNullOrEmpty()) {
            // if no entries, return empty list if "no consumption today" is checked,
            // null otherwise
            return noConsumptionCheckbox.isChecked() ? new JSONArray().toString() : null;
        } else {
            return fieldData.toString();
        }
    }

    @Override
    public void setValue(Object value) {
        try {
            if(value == null) {
                fieldData = null;
                noConsumptionCheckbox.setChecked(false);
            } else {
                fieldData = new JSONArray(value.toString());
                noConsumptionCheckbox.setChecked(fieldData.length() == 0);
            }
            updateView();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void addEntry(JSONObject entry) {
        if (fieldData == null) {
            fieldData = new JSONArray();
        }
        fieldData.put(entry);
        updateView();
        updateObservableFieldData();
    }

    @Override
    public View getView() {
        return fieldView;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (fieldView == null) {
            return;
        }

        for (View button : removeEntryButtons) {
            button.setEnabled(enabled);
        }

        setAddButtonEnabled(!noConsumptionCheckbox.isChecked() && enabled);
        noConsumptionCheckbox.setEnabled(enabled);
    }

    private void setAddButtonEnabled(boolean enabled) {
        addButtonEnabled = enabled;
        addButton.setEnabled(enabled);
        addButton.setBackgroundTintList(ContextCompat.getColorStateList(baseFragment.requireContext(), enabled ? R.color.colorGreen : R.color.colorGray));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setHideNoConsumptionsCheckboxBeforeEvening(boolean hideNoConsumptionsCheckboxBeforeEvening) {
        this.hideNoConsumptionsCheckboxBeforeEvening = hideNoConsumptionsCheckboxBeforeEvening;
    }

    @Override
    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        enumSequenceController.handleActivityResult(requestCode, resultCode, data);
    }



    /**
     * Case-insensitive compare a string with an Enum element.
     *
     * @param strVal
     * @param enumElement
     * @param <E>
     * @return
     */
    private static <E extends Enum<E>> boolean equals(String strVal, E enumElement) {
        return String.valueOf(enumElement).equalsIgnoreCase(strVal);
    }


    private void updateObservableFieldData() {
        if(observableFieldData != null) {
            // observableFieldData must have been acquired at least one time, otherwise we can do without JSON stringification on each modification
            observableFieldData.setValue((String)getValue());
        }
    }

    public Utils.ObservableValue<String> getObservableFieldData() {
        if(observableFieldData == null) {
            observableFieldData = new Utils.ObservableValue<>((String)getValue());
        }
        return observableFieldData;
    }
}

