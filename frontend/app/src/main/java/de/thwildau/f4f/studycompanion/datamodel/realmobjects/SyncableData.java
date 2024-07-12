package de.thwildau.f4f.studycompanion.datamodel.realmobjects;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.enums.DataType;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class SyncableData extends RealmObject {

    private String dataTypeStr;

    private String jsonData = null; // this keeps all the effective data

    @PrimaryKey
    private Long localId;

    @Index
    private String remoteId = null;

    private String userId = BackendIO.getCurrentUser().id; // used only for local data assignment (in case multiple users sign in into the app on same device?)

    private String lastSyncId = null;

    @Required
    private Date creationDate = new Date();
    private Date modificationDate = null;

    private Boolean markedForDeletion = false;

    public SyncableData(Realm realm) {
        localId = Utils.getUniqueLocalId(realm, SyncableData.class);
    }

    public SyncableData() { }

    /**
     * This marks this object as modified and makes it being synced with server on next synchronization process.
     */
    public void invalidate() {
        lastSyncId = null;
        modificationDate = new Date();
    }

    public void fromJsonObject(JSONObject jsonObject, DataType dataType) throws JSONException {
        JSONObject obj = new JSONObject(jsonObject.toString());

        if(obj.has("id")) {
            setRemoteId(obj.getString("id"));
            obj.remove("id");
        }

        if(obj.names().length() == 0) {
            // Object has "id" as its only field - so it's meant to be deleted.
            setMarkedForDeletion(true);
        }

        if(obj.has("creation_time")) {
            try {

                setCreationDate(Utils.getServerTimeFormat().parse(obj.getString("creation_time")));
                //Better: setCreationDate(LocalDateTime.parse(obj.getString("creation_time")));
                // But this would require minimum API level 26 (Android 8)
            } catch (ParseException e) {
                e.printStackTrace();
            }
            obj.remove("creation_time");
        }

        if(obj.has("modification_time")) {
            try {
                setModificationDate(Utils.getServerTimeFormat().parse(obj.getString("modification_time")));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            obj.remove("modification_time");
        }

        setDataType(dataType);
        setJsonData(obj.toString());
    }

    /**
     * Generates an JSON Object of this Data instance, which can be sent to server via PUT /sync/{sync_id}
     * @return
     */
    public JSONObject toRemoteJsonObject() {

        JSONObject jsonObject = new JSONObject();

        try {
            if(isMarkedForDeletion()) {
                if(getRemoteId() == null) {
                    // marked for deletion, but was never uploaded to server,
                    // so can be safely deleted fro local storage
                    return null;
                }

                // Server will delete objects, that only have the ID in their body and nothing more.
                jsonObject.put("id", remoteId);
                return jsonObject;
            }

            jsonObject = new JSONObject(jsonData);

            if(getRemoteId() != null) {
                jsonObject.put("id", remoteId);
            }

            if(getModificationDate() != null) {
                jsonObject.put("modification_time", Utils.getServerTimeFormat().format(modificationDate));
            }
            if(getCreationDate() != null) {
                jsonObject.put("creation_time", Utils.getServerTimeFormat().format(creationDate));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonObject;
    }


    /* Getters and Setters */

    public Long getLocalId() {
        return localId;
    }

    public void setLocalId(Long localId) {
        this.localId = localId;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLastSyncId() {
        return lastSyncId;
    }

    public void setLastSyncId(String lastSyncId) {
        this.lastSyncId = lastSyncId;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    public Boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(Boolean markedForDeletion) {
        this.markedForDeletion = markedForDeletion;
    }

    public DataType getDataType() {
        return DataType.valueOf(dataTypeStr);
    }

    public void setDataType(DataType dataType) {
        this.dataTypeStr = dataType.toString();
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
}
