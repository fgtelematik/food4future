package de.thwildau.f4f.studycompanion.sensors;

import android.bluetooth.BluetoothGattCharacteristic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class GattAttributes {
    public static final String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static final String BODY_SENSOR_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb";
    public static final String TEMPERATURE_MEASUREMENT = "00002a1c-0000-1000-8000-00805f9b34fb";
    public static final String BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb";
    public static final String RAW_DATA = "0000a001-1212-efde-1523-785feabcd123";
    public static final String SIGNAL_QUALITY = "0000a002-1212-efde-1523-785feabcd123";

    public static final String CHARACTERISTIC_CONFIG_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    private static final List<String> relevantCharacteristics;

    static {
        relevantCharacteristics = new ArrayList<>();

        Field[] declaredFields = GattAttributes.class.getDeclaredFields();

        for (Field field : declaredFields) {
            if (
                    java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                    field.getType().equals(String.class)
            ) {
                try {
                    relevantCharacteristics.add((String)field.get(null));
                } catch (IllegalAccessException e) {
                    //
                }
            }
        }
    }

    public static boolean isRelevantCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicStr = characteristic.getUuid().toString().toLowerCase();
        return relevantCharacteristics.contains(characteristicStr);
    }

    public static boolean equals(UUID uuid, String uuidStr) {
        return uuid.toString().equalsIgnoreCase(uuidStr);
    }

}
