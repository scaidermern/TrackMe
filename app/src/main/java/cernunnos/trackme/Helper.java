package cernunnos.trackme;

import android.annotation.SuppressLint;
import android.content.Context;

/**
 * Contains various little helper functions
 */
class Helper {
    /**
     * Converts a distance from meters to an human readable String
     *
     * @param precisionHint  Number of desired digits to show.
     *                       Note that for larger numbers the precision will be reduced by 1 to keep the string short.
     */
    @SuppressLint("DefaultLocale")
    static String humanReadableDistance(final Context context, final double value, final int precisionHint) {
        final int reducedPrecision = precisionHint >= 1 ? precisionHint - 1 : 0;
        if (value >= 1000 * 100) { // >= 100 km
            final String formatNumber = "%." + reducedPrecision + "f";
            return String.format(formatNumber + " %s",
                    value / 1000, context.getString(R.string.distance_unit_kilometers));
        } else if (value >= 1000) { // >= 1 km
            final String formatNumber = "%." + precisionHint + "f";
            return String.format(formatNumber + " %s",
                    value / 1000f, context.getString(R.string.distance_unit_kilometers));
        } else if (value >= 100) { // >= 10 m
            final String formatNumber = "%." + reducedPrecision + "f";
            return String.format(formatNumber + " %s",
                    value, context.getString(R.string.distance_unit_meters));
        } else { // < 1 km
            final String formatNumber = "%." + precisionHint + "f";
            return String.format(formatNumber + " %s",
                    value, context.getString(R.string.distance_unit_meters));
        }
    }

    /** Converts a duration from milliseconds to an human readable String */
    @SuppressLint("DefaultLocale")
    static String humanReadableDuration(final Context context, final long milliSeconds) {
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
