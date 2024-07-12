package de.thwildau.f4f.studycompanion.datamodel;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.StudyCompanion;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.enums.Permission;
import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.ADT;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.Enumeration;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.EnumerationTransition;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.FieldSchema;
import io.realm.Realm;
import io.realm.RealmQuery;

public class SchemaProvider {
    public interface DownloadProcessFinishedCallback {
        void onProcessFinished(boolean withError, boolean configUpdated);
    }

    public static class EnumMetadata {
        private String label;
        private String helpText;
        private boolean containsFoodItems;

        public EnumMetadata(String label, String helpText, boolean containsFoodItems) {
            this.label = label;
            this.helpText = helpText;
            this.containsFoodItems = containsFoodItems;
        }

        @NonNull
        public String getLabel() {
            return label == null ? "" : label;
        }

        @NonNull
        public String getHelpText() {
            return helpText == null ? "" : helpText;
        }

        @NonNull
        public boolean containsFoodItems() {
            return containsFoodItems;
        }
    }

    private static DeviceConfig cachedDeviceConfig = null;
    private static int structureDownloadCounter = 0;
    private static boolean structureDownloadError = false;
    private static boolean configUpdated = false;

    private static EnumImageProvider enumImageProvider;

    private static void executeRealmTransaction(Realm.Transaction transaction) {
        Realm r = Realm.getDefaultInstance();
        r.executeTransaction(transaction);
        r.close();
    }

    private static boolean initStructureDownloadProcess(int numberOfStructuresToDownload) {
        if(structureDownloadCounter != 0) {
            // structure download already running
            return false;
        }

        structureDownloadCounter = numberOfStructuresToDownload;
        return true;
    }

    private static void checkFinishedStructureDownload(DownloadProcessFinishedCallback processFinishedCallback, boolean error) {
        structureDownloadCounter--;
        if(error) {
            structureDownloadError = true;
        }
        if(structureDownloadCounter > 0) {
            // not all download request have been processed yet
            return;
        }

        // all download requests processed. Notify callback, if available.

        boolean wasStructureDownloadError = structureDownloadError;
        boolean wasConfigUpdated = configUpdated;

        structureDownloadError = false;
        structureDownloadCounter = 0;
        configUpdated = false;

        if(processFinishedCallback != null) {
            processFinishedCallback.onProcessFinished(wasStructureDownloadError, wasConfigUpdated);
        }

    }

    /**
     * Download field schema, enum, ADT definitions and device config in JSON format from server
     * and store them in local MongoDB Realm database.
     *
     * This action is done asynchronously
     */
    public static void downloadSturcturesFromServer(DownloadProcessFinishedCallback processFinishedCallback) {
        if(!initStructureDownloadProcess(4)) {
//            if(processFinishedCallback != null) {
//                BackendIO.serverLog(Log.WARN, "SchemaProvider", "Schema download requested with specified callback, but downloading process is already running. ");
//                Thread.dumpStack();
//            }
            return;
        }

        // asynchronously fetch field schema definitions from server:
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.SCHEMAS, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    JSONArray schemas = response.getJSONArray("schemas");

                    for(int i = 0; i < schemas.length(); i++) {
                        JSONObject schema = schemas.getJSONObject(i);
                        String name = schema.getString("id");
                        storeLocalFieldSchema(name, schema.toString());
                    }
                    checkFinishedStructureDownload(processFinishedCallback, false);
                } catch (JSONException e) {
                    checkFinishedStructureDownload(processFinishedCallback, true);
                    e.printStackTrace();
                }

            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                checkFinishedStructureDownload(processFinishedCallback, true);
                handleNetworkError(errorStatusCode, errorMessage);
            }
        });

        // asynchronously fetch enum definitions from server:
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.ENUMS, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {

                    // Process enums
                    JSONArray enums = response.getJSONArray("enums");

                    if(enumImageProvider == null) {
                        enumImageProvider = new EnumImageProvider(StudyCompanion.getAppContext());
                    }

                    // download new enum images and store in app cache, if necessary
                    enumImageProvider.processNewEnumArray(enums);

                    for(int i = 0; i < enums.length(); i++) {
                        JSONObject enumeration = enums.getJSONObject(i);
                        String name = enumeration.getString("id");
                        String jsonEnumElementsString = enumeration.toString();
                        storeLocalEnum(name, jsonEnumElementsString);
                    }

                    // Process enum transitions:
                    JSONObject enumTransitions = response.getJSONObject("enum_transitions");

                    Iterator<String> keys = enumTransitions.keys();
                    while(keys.hasNext()) {
                        String enumId = keys.next();
                        String transitionJSON = enumTransitions.getJSONObject(enumId).toString();
                        storeLocalEnumTransition(enumId, transitionJSON);
                    }

                    checkFinishedStructureDownload(processFinishedCallback, false);
                } catch (JSONException e) {
                    checkFinishedStructureDownload(processFinishedCallback, true);
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                checkFinishedStructureDownload(processFinishedCallback, true);
                handleNetworkError(errorStatusCode, errorMessage);
            }
        });

        // asynchronously fetch ADT definitions from server:
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.ADTS, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {

                    Iterator<String> adtIds = response.keys();

                    while(adtIds.hasNext()) {
                        String adtId = adtIds.next();
                        JSONArray adtFieldArray = response.getJSONArray(adtId);
                        storeLocalADT(adtId, adtFieldArray.toString());
                    }
                    checkFinishedStructureDownload(processFinishedCallback, false);
                } catch (JSONException e) {
                    checkFinishedStructureDownload(processFinishedCallback, true);
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                checkFinishedStructureDownload(processFinishedCallback, true);
                handleNetworkError(errorStatusCode, errorMessage);
            }
        });

        // asynchronously fetch Device Config definition from server:
        BackendIO.getRemoteDatasetAsync(BackendIO.RemoteDatasetType.CONFIG, new BackendIO.RemoteRequestCompletedCallback() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    configUpdated = storeLocalDeviceConfig(response);
                    checkFinishedStructureDownload(processFinishedCallback, false);
                } catch (JSONException e) {
                    checkFinishedStructureDownload(processFinishedCallback, true);
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int errorStatusCode, String errorMessage) {
                checkFinishedStructureDownload(processFinishedCallback, true);
                handleNetworkError(errorStatusCode, errorMessage);
            }
        });
    }

    private static void handleNetworkError(int errorStatusCode, String errorMessage) {
        //Toast.makeText(StudyCompanion.getAppContext(), "Error loading data structure information from server ("+errorStatusCode+": "+errorMessage+").", Toast.LENGTH_LONG).show();
        //TODO: Do something / Inform the user in an understandable way, if even after multiple attempts the schemas can not be downloaded.
    }

    private static void storeLocalFieldSchema(String name, String jsonSchemaString) {
        executeRealmTransaction(r -> {
            FieldSchema schema = new FieldSchema();
            schema.id = name;
            schema.jsonSchema = jsonSchemaString;
            r.insertOrUpdate(schema);
        });
    }

    private static void storeLocalEnum(String name, String jsonEnumElementsString) {
        executeRealmTransaction(r -> {
            Enumeration enumeration = new Enumeration();
            enumeration.id = name;
            enumeration.jsonSchema = jsonEnumElementsString;
            r.insertOrUpdate(enumeration);
        });
    }

    private static void storeLocalEnumTransition(String enumId, String jsonEnumTransition) {
        executeRealmTransaction(r -> {
            EnumerationTransition enumTransition = new EnumerationTransition();
            enumTransition.enumId = enumId;
            enumTransition.transitionJson = jsonEnumTransition;
            r.insertOrUpdate(enumTransition);
        });
    }

    private static void storeLocalADT(String name, String jsonADTFieldsString) {
        executeRealmTransaction(r -> {
            ADT adt = new ADT();
            adt.id = name;
            adt.jsonSchema = jsonADTFieldsString;
            r.insertOrUpdate(adt);
        });
    }

    /**
     *
     * @param deviceConfigJson
     * @return true, if config was updated
     * @throws JSONException
     */
    private static boolean storeLocalDeviceConfig(JSONObject deviceConfigJson) throws JSONException {
        String deviceConfigString = deviceConfigJson.toString();
        String prefName = StudyCompanion.getAppContext().getString(R.string.deviceConfig);

        if(StudyCompanion.getGlobalPreferences().getString(prefName, "").equals(deviceConfigString)) {
            // Config json string does not differ compared to last time downloaded
            return false;
        }

        // Config has been updated.

        cachedDeviceConfig = new DeviceConfig(deviceConfigString);

        // Store device config on device to make it accessible without internet connection
        // Local device config is stored as JSON String in Shared Preferences, not as Realm object

        StudyCompanion.getGlobalPreferences().edit().putString(prefName, deviceConfigString).apply();

        return true;
    }

    public static DeviceConfig getDeviceConfig() {
        if(cachedDeviceConfig != null) {
            // Use cached config, if already created
            return cachedDeviceConfig;
        }

        // Try to restore device config from local storage
        String prefName = StudyCompanion.getAppContext().getString(R.string.deviceConfig);
        String storedDeviceConfigJson = PreferenceManager.getDefaultSharedPreferences(StudyCompanion.getAppContext()).getString(prefName, null);
        if(storedDeviceConfigJson != null) {
            try {
                cachedDeviceConfig = new DeviceConfig(storedDeviceConfigJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(cachedDeviceConfig == null) {
            // Using local default configuration, if neither cached version is available nor stored version could be found or properly interpreted
            cachedDeviceConfig = new DeviceConfig();
        }

        return cachedDeviceConfig;
    }

    public static boolean checkPermissionForField(JSONObject fieldSchemaObj, Permission fieldPermission) throws JSONException {
        if(BackendIO.getCurrentUser() == null)
            // user is logged in => has no permission for nothing.
            return false;

        if(fieldSchemaObj.getString("id").equals("id") && fieldPermission != Permission.Read) {
            // The 'id' field is reserved and exists for all data sets.
            // The id will be assigned from server on dataset creation and can never be modified on front-end side.
            return false;
        }

        if(BackendIO.getCurrentUser().role == Role.Administrator) {
            // Administrator has all permissions by default.
            return true;
        }

        if(!fieldSchemaObj.has("permissions")) {
            // field has no further permissions defined => is only editable by Administrator
            return false;
        }


        JSONArray fieldPermissions =  fieldSchemaObj.getJSONArray("permissions");
        Role currentRole = BackendIO.getCurrentUser().role;

        for(int i = 0; i < fieldPermissions.length(); i++) {
            JSONObject permission = fieldPermissions.getJSONObject(i);
            String permRole = permission.getString("role");
            String permType = permission.getString("type");

            if(
                    (permRole.equals("all") ||
                    permRole.equals(currentRole.toString()) ) &&
                    permType.equals(fieldPermission.toString())
            ) {
                // A corresponding entry is found in the permission list for this field.
                return true;
            }
        }

        return false;
    }

    public static JSONObject getSchemaForField(String id) {
        Realm r = Realm.getDefaultInstance();
        RealmQuery<FieldSchema> schemaQuery = r.where(FieldSchema.class).equalTo("id", id);
        FieldSchema schema = schemaQuery.findFirst();
        JSONObject res = null;

        if(schema != null) {
            try {
                res = new JSONObject(schema.jsonSchema);
            } catch (JSONException e) {
                Toast.makeText(StudyCompanion.getAppContext(), "Error on parsing JSON schema for field '"+id+"'.", Toast.LENGTH_LONG).show();
                r.close();
                return null;
            }
        } else {
            r.close();
            return null;
        }
        r.close();
        return res;
    }

    public static JSONObject getEnumTransitions(String enumID) {
        Realm r = Realm.getDefaultInstance();
        RealmQuery<EnumerationTransition> schemaQuery = r.where(EnumerationTransition.class).equalTo("enumId", enumID);
        EnumerationTransition enumerationTransition = schemaQuery.findFirst();

        if(enumerationTransition == null) {
            r.close();
            return null;
        }

        try {
            JSONObject res =new JSONObject(enumerationTransition.transitionJson);
            r.close();
            return  res;
        } catch (JSONException e) {
            r.close();
            return null;
        }
    }

    public static EnumMetadata getEnumMetadata(String enumID) {
        Realm r = Realm.getDefaultInstance();
        RealmQuery<Enumeration> schemaQuery = r.where(Enumeration.class).equalTo("id", enumID);
        Enumeration enumeration = schemaQuery.findFirst();

        String label = null;
        String helpText = null;
        boolean containsFoodItems = false;

        try {
            JSONObject enumElementObject = new JSONObject(enumeration.jsonSchema);
            label = enumElementObject.optString("label");
            helpText = enumElementObject.optString("helpText");
            containsFoodItems = enumElementObject.optBoolean("contains_food_items");
        } catch (Throwable e) {
            // ignore
        }

        r.close();
        return new EnumMetadata(label, helpText, containsFoodItems);
    }

    public static List<EnumerationElement> getEnumElements(String enumID) {
        Realm r = Realm.getDefaultInstance();
        RealmQuery<Enumeration> schemaQuery = r.where(Enumeration.class).equalTo("id", enumID);
        Enumeration enumeration = schemaQuery.findFirst();

        List<EnumerationElement> res = new ArrayList<>();

        if(enumeration != null) {
            try {
                JSONObject enumElementObject = new JSONObject(enumeration.jsonSchema);

                JSONArray jsonIds = enumElementObject.getJSONArray("element_ids");
                JSONArray jsonLabels = enumElementObject.getJSONArray("element_labels");
                JSONArray jsonExplicitLabels = enumElementObject.optJSONArray("element_explicit_labels");

                for(int i = 0; i < jsonIds.length(); i++) {
                    String elementId = jsonIds.getString(i);
                    String elementLabel = jsonLabels.getString(i);
                    String explicitLabel = null;

                    if(jsonExplicitLabels != null) {
                        if(!jsonExplicitLabels.isNull(i)) { // explicit check for null required due to bug in JSON library: https://stackoverflow.com/q/18226288/5106474
                            explicitLabel = jsonExplicitLabels.optString(i);
                        }
                    }

                    res.add(new EnumerationElement(enumID, elementId, elementLabel, explicitLabel));
                }
                r.close();
                return res;
            } catch (JSONException e) {
                Toast.makeText(StudyCompanion.getAppContext(), "Error on parsing JSON schema for enumeration '"+enumID+"'.", Toast.LENGTH_LONG).show();
                r.close();
                return null;
            }
        } else {
            r.close();
            return null;
        }
    }

    public static List<String> getADTFields(String adtId) {
        Realm r = Realm.getDefaultInstance();
        RealmQuery<ADT> schemaQuery = r.where(ADT.class).equalTo("id", adtId);
        ADT schema = schemaQuery.findFirst();

        List<String> res = new ArrayList<>();

        if(schema != null) {
            try {
                JSONArray fields = new JSONArray(schema.jsonSchema);
                for(int i = 0; i < fields.length(); i++) {
                    res.add(fields.getString(i));
                }
                r.close();
                return res;
            } catch (JSONException e) {
                Toast.makeText(StudyCompanion.getAppContext(), "Error on parsing JSON schema for ADT '"+adtId+"'.", Toast.LENGTH_LONG).show();
                r.close();
                return null;
            }
        } else {
            r.close();
            return null;
        }
    }

    /**
     * Displaying a field might be restricted when displayPeriodDays and (optionally) displayDayOne is set.
     * This checks for these restrictions for the specified field.
     * @return true if the field should be displayed, false otherwise
     */
    public static boolean checkDisplayConstraint(Integer currentOffsetDay, JSONObject fieldSchema) {
        if(currentOffsetDay == null || !fieldSchema.has("displayPeriodDays")) {
            return true;
        }

        try {
            int displayPeriodDays = fieldSchema.getInt("displayPeriodDays");
            boolean displayDayOne = fieldSchema.optBoolean("displayDayOne");

            if(currentOffsetDay == 0 ) {
                // effective day is day One
                return displayDayOne;
            }

            // return true only, if currentOffsetDay is a multiple of displayPeriodDays
            if(displayDayOne)
                // started counting from the day AFTER study_begin_date, if displayDayOne is set
                // to make sure, the interval between the days the field is shown, is always equal
                return (currentOffsetDay) % displayPeriodDays == 0;
            else
                return (currentOffsetDay + 1) % displayPeriodDays == 0;


        } catch (JSONException e) {
            // ignore
        }

        return true;
    }
}
