package de.thwildau.f4f.studycompanion.sensors.cosinuss;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class CosinussHeartMeasurement {
    private final List<Float> rrValues;
    private final int bpm;

    public static CosinussHeartMeasurement FromCharacteristicValue(byte[] value) {
        // interpret characteristic value according to Gatt specification
        //     (simplified for Cosinuss One, where contact and energy expended status is not available)
        int bpm;
        List<Float> rrValues = new ArrayList<>();

        int bytePos = 0;
        byte flags = value[bytePos];
        bytePos++;

        if ((flags & 1) > 0) {
            // heart rate format is uint16
            bpm = (value[bytePos + 1] << 8) | value[bytePos];
            bytePos += 2;
        } else {
            // heart rate format is uint8
            bpm = value[bytePos];
            bytePos += 1;
        }

        if ((flags & (1 << 3)) > 0) {
            // Energy Expended field (16 Bit) is present, but won't be used
            // so we skip it
            bytePos += 2;
        }

        if ((flags & (1 << 4)) > 0) {
            // One or more RR-Interval values (uint16) are present.
            while (bytePos < value.length) {
                int msb = (value[bytePos + 1] & 0xFF) << 8; // & 0xFF to make byte unsigned!
                int lsb = value[bytePos] & 0xFF;
                int raw = msb | lsb;
                float rrVal = raw / 1024.0f;
                rrValues.add(rrVal);
                bytePos += 2;
            }
        }

        return new CosinussHeartMeasurement(rrValues, bpm);
    }

    private CosinussHeartMeasurement(List<Float> rrValues, int bpm) {
        this.rrValues = rrValues;
        this.bpm = bpm;
    }

    public List<Float> getRRValues() {
        return rrValues;
    }

    public int getBpm() {
        return bpm;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("CosinussHeartMeasurement (BPM = " + bpm);

        if (rrValues.size() > 0) {
            String sep = "";
            str.append(", RR-Intervalls =[");
            for (Float rrVal : rrValues) {
                str.append(sep).append(rrVal);
                sep =", ";
            }
            str.append("]");
        }

        str.append(")");
        return str.toString();
    }
}
