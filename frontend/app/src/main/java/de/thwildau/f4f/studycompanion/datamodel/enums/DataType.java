package de.thwildau.f4f.studycompanion.datamodel.enums;

import androidx.annotation.NonNull;

public enum DataType {
    UserData("UserData"),
    LabData("LabData"),
    StaticData("StaticData"),
    SensorData("SensorData");

    private String typeId;

    DataType(String typeId) {
        this.typeId = typeId;
    }

    @NonNull
    @Override
    public String toString() {
        return typeId;
    }

}
