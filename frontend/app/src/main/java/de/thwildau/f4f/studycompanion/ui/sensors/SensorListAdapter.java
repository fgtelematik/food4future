package de.thwildau.f4f.studycompanion.ui.sensors;

import android.content.Context;
import android.opengl.Visibility;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.sensors.interfaces.ISensorDevice;

public class SensorListAdapter extends ArrayAdapter<ISensorDevice> {

    private static class ViewHolder {
        TextView textName;
        TextView textAddress;
    }

    public SensorListAdapter(@NonNull Context context, @NonNull List<ISensorDevice> objects) {
        super(context, R.layout.scanned_sensor_item, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ISensorDevice device = getItem(position);
        ViewHolder viewHolder;
        View resView;

        String name = device.getName();
        String address = device.getMacAddress();

        if(convertView == null) {
            viewHolder = new ViewHolder();
            resView = LayoutInflater.from(getContext()).inflate(R.layout.scanned_sensor_item, parent, false);
            viewHolder.textAddress = resView.findViewById(R.id.textAddress);
            viewHolder.textName = resView.findViewById(R.id.textName);
            resView.setTag(viewHolder);
        } else {
            resView = convertView;
            viewHolder = (ViewHolder)resView.getTag();
        }

        viewHolder.textName.setText( name == null ? "" : name);
        viewHolder.textName.setVisibility(name == null ? View.GONE : View.VISIBLE);

        viewHolder.textAddress.setText(address == null ? "" : address);
        viewHolder.textAddress.setVisibility(address == null ? View.GONE : View.VISIBLE);

        return resView;
    }
}
