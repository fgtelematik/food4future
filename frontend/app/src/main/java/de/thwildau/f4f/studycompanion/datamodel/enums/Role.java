package de.thwildau.f4f.studycompanion.datamodel.enums;

import androidx.annotation.NonNull;

public enum Role {
    Administrator("Administrator"),
    Nurse("Nurse"),
    Supervisor("Supervisor"),
    Participant("Participant");

    private String roleId;

    Role(String roleId) {
        this.roleId = roleId;
    }

    @NonNull
    @Override
    public String toString() {
        return roleId;
    }
}
