package cernunnos.trackme;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Contains a single location. This location is both parcelable as well as serializable.
 *
 * This object is very similar to Android's Location class. However Android's Location class
 * is not serializable.
 *
 * Additionally we can save some memory by storing only the interesting parts of a Location
 * object (but waste some more CPU cycles during conversion)
 */
class MyLocation implements Parcelable, Serializable {
    double latitude;
    double longitude;
    long time;
    boolean hasSpeed;
    float speed;
    boolean hasAccuracy;
    float accuracy;

    /** creates a new location object */
    MyLocation(final Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        time = location.getTime();
        hasSpeed = location.hasSpeed();
        speed = hasSpeed ? location.getSpeed() : 0.0f;
        hasAccuracy = location.hasAccuracy();
        accuracy = hasAccuracy ? location.getAccuracy() : 0.0f;
    }

    /** computes the distance in meters between two locations */
    float distanceTo(final MyLocation other) {
        float dist[] = new float[1];
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, dist);
        return dist[0];
    }

    /** creates a new location from a Parcel */
    private MyLocation(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        time = in.readLong();
        speed = in.readFloat();
        accuracy = in.readFloat();
    }

    /** flattens this location list into a Parcel */
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeDouble(latitude);
        parcel.writeDouble(longitude);
        parcel.writeLong(time);
        parcel.writeFloat(speed);
        parcel.writeFloat(accuracy);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MyLocation> CREATOR = new Creator<MyLocation>() {
        @Override
        public MyLocation createFromParcel(Parcel in) {
            return new MyLocation(in);
        }

        @Override
        public MyLocation[] newArray(final int size) {
            return new MyLocation[size];
        }
    };
}
