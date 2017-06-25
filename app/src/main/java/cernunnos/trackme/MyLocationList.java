package cernunnos.trackme;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Contains a list of locations. This list is both parcelable as well as serializable.
 *
 * The last location contained in the list should be always the newest one.
 */
class MyLocationList implements Parcelable, Serializable {

    class Statistics implements Serializable {
        // distance in total (in meters)
        double distanceTotal = 0;
        // distance from the last location measurement (in meters)
        double distanceLast = 0;
        // average speed
        double speedAvg = 0.0;
        // speed since the last location measurement (in km/h)
        double speedLast = 0.0;
        // max speed (in km/h)
        double speedMax = 0.0;
        // duration (in milliseconds)
        long duration = 0;

        Statistics(final ArrayDeque<MyLocation> locations) {
            if (locations.size() > 0) {
                final MyLocation lastLocation = locations.getLast();
                if (lastLocation.hasSpeed) {
                    speedLast = lastLocation.speed * 3.6;
                }
            }

            if (locations.size() > 1) {
                final Iterator<MyLocation> it = locations.iterator();
                MyLocation prevLocation = it.next();
                speedAvg = prevLocation.speed;
                speedMax = prevLocation.speed;
                while (true) {
                    final MyLocation curLocation = it.next();
                    distanceTotal += prevLocation.distanceTo(curLocation);
                    speedAvg += curLocation.speed;
                    speedMax = Math.max(speedMax, curLocation.speed);

                    if (!it.hasNext()) {
                        distanceLast = curLocation.distanceTo(prevLocation);
                        break;
                    }

                    prevLocation = curLocation;
                }
                speedAvg /= locations.size();

                // convert from m/s to km/h
                speedAvg *= 3.6;
                speedMax *= 3.6;

                duration = locations.getLast().time - locations.getFirst().time;
            }
        }
    }

    // list of locations
    private ArrayDeque<MyLocation> locations;

    // location statistics, doesn't need to be serializable
    private transient Statistics statistics;

    /* Add a new location */
    void addLast(final MyLocation location) {
        locations.addLast(location);
        statistics = null;
    }

    /** Remove the oldest location */
    void removeFirst() {
        locations.removeFirst();
        statistics = null;
    }

    /** Clear all locations */
    void clear() {
        locations.clear();
        statistics = null;
    }

    /** Return the number of stored locations */
    int size() {
        return locations.size();
    }

    /** Return the first (=oldest) location or null if empty */
    MyLocation getFist() {
        return locations.isEmpty() ? null : locations.getFirst();
    }

    /** Return the last (=newest) location or null if empty */
    MyLocation getLast() {
        return locations.isEmpty() ? null : locations.getLast();
    }

    /** Return various statistics about the stored locations */
    Statistics getStatistics() {
        // cache result
        if (statistics == null) {
            statistics = new Statistics(locations);
        }
        return statistics;
    }

    /**
     * Return a copy of the list of locations.
     * Note: Don't return the actual list since modifying it will make our statistics cache wrong.
     */
    ArrayDeque<MyLocation> get() {
        return new ArrayDeque<>(locations);
    }

    /** Create a new empty location list */
    MyLocationList() {
        locations = new ArrayDeque<>();
    }

    /** Create a new location list of the given size */
    public MyLocationList(final int size) {
        locations = new ArrayDeque<>(size);
    }

    /** Create a new location list from a Parcel */
    private MyLocationList(Parcel in) {
        int size = in.readInt();
        locations = new ArrayDeque<>(size);
        for (int i = 0; i < size; ++i) {
            final MyLocation location = in.readParcelable(MyLocation.class.getClassLoader());
            locations.addLast(location);
        }
    }

    /** Flatten this location list into a Parcel */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(locations.size());
        for (final MyLocation location : locations) {
            out.writeParcelable(location, flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MyLocationList> CREATOR = new Creator<MyLocationList>() {
        @Override
        public MyLocationList createFromParcel(Parcel in) {
            return new MyLocationList(in);
        }

        @Override
        public MyLocationList[] newArray(final int size) {
            return new MyLocationList[size];
        }
    };
}
