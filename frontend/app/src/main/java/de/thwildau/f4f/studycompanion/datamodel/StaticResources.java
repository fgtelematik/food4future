package de.thwildau.f4f.studycompanion.datamodel;

import android.os.Parcel;
import android.os.Parcelable;

public class StaticResources {

    // The following fields are interpreted automatically via reflections by StaticResourcesProvider class.

    // Static resources defined here will automatically be cached. The cache will be updated every time the MainActivity is launched
    // given that the device has internet access. If "updateOnWifiOnly" is set, files are only cached when the device is using
    // an unmetered internet connection.

    // All static resources MUST be defined as "public final static StaticResource". Otherwise they will be ignored.

    public final static StaticResource IMAGE_LICENSE_INFO_HTML = new StaticResource("images/sources.html", false);



    // internal implementation

    public static class StaticResource implements Parcelable {

        private final String path;
        private final boolean updateOnWifiOnly;

        public StaticResource(String path, boolean updateOnWifiOnly) {
            this.path = path;
            this.updateOnWifiOnly = updateOnWifiOnly;
        }

        protected StaticResource(Parcel in) {
            path = in.readString();
            updateOnWifiOnly = in.readByte() != 0;
        }

        public static final Creator<StaticResource> CREATOR = new Creator<StaticResource>() {
            @Override
            public StaticResource createFromParcel(Parcel in) {
                return new StaticResource(in);
            }

            @Override
            public StaticResource[] newArray(int size) {
                return new StaticResource[size];
            }
        };

        public String getPath() {
            return path;
        }

        public boolean isUpdateOnWifiOnly() {
            return updateOnWifiOnly;
        }

        public String getFileExtension() {
            if(path.contains(".")) {
                return path.substring(path.lastIndexOf(".") + 1);
            } else {
                return null;
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(path);
            dest.writeByte((byte) (updateOnWifiOnly ? 1 : 0));
        }
    }


}
