package de.thwildau.f4f.studycompanion.datamodel.realmobjects;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

public class FieldSchema  extends RealmObject {
    @Required
    @PrimaryKey
    public String id;
    public String jsonSchema;
}
