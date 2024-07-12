package de.thwildau.f4f.studycompanion.datamodel.enums;

import androidx.annotation.NonNull;

public enum Permission {
    Read("Read"),
    Edit("Edit"),
    AddElement("AddElement"),
    RemoveElement("RemoveElement");

    private String permissionId;

    Permission(String permissionId) {
        this.permissionId = permissionId;
    }

    @NonNull
    @Override
    public String toString() {
        return permissionId;
    }
}
