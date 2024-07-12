package de.thwildau.f4f.studycompanion.datamodel.enums;

import androidx.annotation.NonNull;

public enum SensorDataType {
    Energy("Energy"),
    Pulse("Pulse"),
    Temperature("Temperature"),
    PulseOx("PulseOx"),
    HRV("HRV"),
    Acceleration("Acceleration"),
    Respiration("Respiration"),
    StepCount("StepCount"),
    StressScore("StressScore");

    private String typeId;

    SensorDataType(String typeId) {
        this.typeId = typeId;
    }

    @NonNull
    @Override
    public String toString() {
        return typeId;
    }

}
