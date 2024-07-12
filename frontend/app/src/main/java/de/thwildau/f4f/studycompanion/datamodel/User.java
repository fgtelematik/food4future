package de.thwildau.f4f.studycompanion.datamodel;

import androidx.annotation.NonNull;

import de.thwildau.f4f.studycompanion.datamodel.enums.Role;
import io.realm.RealmObject;

public class User  {
    public String firstName;
    public String lastName;
    public String username;
    public Role role;
    public String id;
    public String anamnesisData = null;

    @NonNull
    @Override
    public String toString() {
        return firstName + " " + lastName;
    }


}
