package cernunnos.trackme;

import android.annotation.SuppressLint;
import android.content.Context;

/**
 * Contains various little helper functions
 */
public class Helper {
    /** converts a distance from meters to an human readable String */
    @SuppressLint("DefaultLocale")
    public static String humanReadableDistance(final Context context, final double value) {
        if (value >= 1000 * 100) { // >= 100 km
            return String.format("%.0f %s",
                    value / 1000, context.getString(R.string.distance_unit_kilometers));
        } else if (value >= 1000) { // >= 1 km
            return String.format("%.1f %s",
                    value / 1000f, context.getString(R.string.distance_unit_kilometers));
        } else if (value >= 100) { // >= 10 m
            return String.format("%.0f %s",
                    value, context.getString(R.string.distance_unit_meters));
        } else { // < 1 km
            return String.format("%.1f %s",
                    value, context.getString(R.string.distance_unit_meters));
        }
    }

    /** converts a duration from milliseconds to an human readable String */
    @SuppressLint("DefaultLocale")
    public static String humanReadableDuration(final Context context, final long milliSeconds) {
        long tmp = milliSeconds / 1000;
        long seconds = tmp % 60;
        tmp /= 60;
        long minutes = tmp % 60;
        tmp /= 60;
        long hours = tmp % 24;
        tmp /= 24;
        long days = tmp;
        if (days == 0) {
            return String.format("%02d:%02d:%02d",
                    hours, minutes, seconds);
        } else {
            return String.format("%d %s, %02d:%02d:%02d",
                    days, context.getString(R.string.time_unit_days), hours, minutes, seconds);
        }
    }
}
