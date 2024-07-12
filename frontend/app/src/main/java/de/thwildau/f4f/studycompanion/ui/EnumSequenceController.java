package de.thwildau.f4f.studycompanion.ui;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.qr.QRCodeReaderActivity;
import de.thwildau.f4f.studycompanion.ui.questions.ImageEnumActivity;


public class EnumSequenceController {
    private static final String TAG_SEPARATOR = "+";

    private final Fragment baseFragment;
    private final int requestCode;
    private final Utils.ObservableValue<JSONObject> observableResultDataset;

    private String currentEnumId = null;
    private LinkedList<String> enumHistory;
    private LinkedList<List<String>> tagsHistory;
    private JSONObject newDatatset;
    private final List<String> tags = new ArrayList<>();


    public EnumSequenceController(Fragment baseFragment, int requestCode) {
        this.baseFragment = baseFragment;
        this.requestCode = requestCode;
        this.observableResultDataset = new Utils.ObservableValue<>(null);
    }

    public Utils.ObservableValue<JSONObject> getObservableResultDataset() {
        return observableResultDataset;
    }

    public void startSequence(String startEnumId) {
        newDatatset = new JSONObject();
        enumHistory = new LinkedList<>();
        tagsHistory = new LinkedList<>();
        currentEnumId = startEnumId;
        nextUserInput();
    }

    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == this.requestCode) {
            if (resultCode == Activity.RESULT_OK) {
                processResult(data);
            } else {
                // User pressed back button

                String lastEnumId = enumHistory.isEmpty() ? null : enumHistory.pop();

                if (newDatatset != null && lastEnumId != null) {
                    newDatatset.remove(lastEnumId);
                }
                currentEnumId = lastEnumId;

                // Remove tags which where added by previous transition (might be an empty list of tags)
                if(!tagsHistory.isEmpty()) { // List is empty when cancelling very first enum
                    tags.removeAll(tagsHistory.pop());
                }

                nextUserInput();
            }
        }
    }


    private void nextUserInput() {
        if (currentEnumId == null) {
            return;
        }
        Intent i;

        i = new Intent(baseFragment.getActivity(), ImageEnumActivity.class);
        i.putExtra(ImageEnumActivity.EXTRA_ENUM_ID, currentEnumId);

        SchemaProvider.EnumMetadata enumMetaData = SchemaProvider.getEnumMetadata(currentEnumId);
        String title = enumMetaData.getLabel();
        String message = enumMetaData.getHelpText();

        i.putExtra(ImageEnumActivity.EXTRA_TITLE, title);
        if (!Utils.nullOrEmpty(message))
            i.putExtra(ImageEnumActivity.EXTRA_INFOTEXT, message);

        baseFragment.startActivityForResult(i, requestCode);
    }



    private void processResult(Intent data) {
        // Extract value string
        String value = null;
        String selectedId = null;

        // acquire non-enum data if available
        if (data.hasExtra(ImageEnumActivity.EXTRA_OTHERS_NUMBER_VALUE)) {
            double valDouble = data.getDoubleExtra(ImageEnumActivity.EXTRA_OTHERS_NUMBER_VALUE, 0);
            value = Double.toString(valDouble);
            if ((valDouble == Math.floor(valDouble))) {
                // Cut decimal part in value string if value is a whole number
                value = value.split(Pattern.quote("."))[0];
            }

            if (data.hasExtra(ImageEnumActivity.EXTRA_OTHERS_NUMBER_UNIT_STRING)) {
                value += " " + data.getStringExtra(ImageEnumActivity.EXTRA_OTHERS_NUMBER_UNIT_STRING);
            }

        } else if (data.hasExtra(ImageEnumActivity.EXTRA_OTHERS_STRING_VALUE)) {
            value = data.getStringExtra(ImageEnumActivity.EXTRA_OTHERS_STRING_VALUE);
        }


        if (data.hasExtra(ImageEnumActivity.EXTRA_SELECTED_ENTRY)) {
            selectedId = data.getStringExtra(ImageEnumActivity.EXTRA_SELECTED_ENTRY);
            if(value == null) {
                // basic enum item selected. Use Item ID as value
                value = selectedId;
            }
        }

        // Apply data to current new entry
        try {
            newDatatset.put(currentEnumId, value);
        } catch (JSONException e) {
            //
        }

        // Analyse next enum in enum sequence
        JSONObject transitions = SchemaProvider.getEnumTransitions(currentEnumId);
        String newEnumId = null;

        if (transitions != null) {
            Iterator<String> transitionKeys = transitions.keys();
            String matchedKey = null;
            while (transitionKeys.hasNext()) {
                String key = transitionKeys.next();
                if (matchKey(key, selectedId)) {
                    matchedKey = key; // use the last matching key as effective transition
                }
            }

            if (matchedKey != null && !transitions.isNull(matchedKey)) {
                String transitionValue = transitions.optString(matchedKey);
                if (transitionValue != null) {
                    newEnumId = extractTargetEnumAndStoreTags(transitionValue);
                }
            }
        }

        // Apply next enum from enum sequence, or null if sequence terminated
        enumHistory.push(currentEnumId);
        currentEnumId = newEnumId;

        if (newEnumId == null) {
            addFoodId();
            observableResultDataset.setValue(newDatatset);
            Log.d("EnumSequenceController", "Result dataset: " + newDatatset.toString());
        } else {
            nextUserInput();
        }
    }

    /**
     * If the enum history contains at least one enum with food items,
     * add the enum's selected element id as food_id field value to the dataset.
     */
    private void addFoodId() {
        String foodId = null;
        for (String enumName : enumHistory
        ) {
            if(SchemaProvider.getEnumMetadata(enumName).containsFoodItems()) {
                foodId = newDatatset.optString(enumName);
                break; // we traverse backwards in the history, so the first enum with food items is the last one
            }
        }

        if(foodId != null) {
            try {
                newDatatset.put("food_id", foodId);
            } catch (JSONException e) {
                //
            }
        }
    }

    private String extractTargetEnumAndStoreTags(String transitionValue) {
        List<String> elements = new LinkedList<>(Arrays.asList(transitionValue.split(Pattern.quote(TAG_SEPARATOR))));
        String targetEnum = elements.get(0);
        elements.remove(0);
        tags.addAll(elements);
        tagsHistory.push(elements);
        return targetEnum;
    }

    private boolean matchKey(String key, String selectedItem) {
        List<String> keyParts = new LinkedList<>(Arrays.asList(key.split(Pattern.quote(TAG_SEPARATOR))));
        key = keyParts.get(0);
        keyParts.remove(0);
        for (String keyTag : keyParts) {
            if (!tags.contains(keyTag)) {
                // There is a tag in the key's tag list, which was not defined in the current enum sequence
                return false;
            }
        }

        return key.equals("*") || key.equals(selectedItem);
    }

}
