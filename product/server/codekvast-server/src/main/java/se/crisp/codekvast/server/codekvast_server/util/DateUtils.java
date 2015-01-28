package se.crisp.codekvast.server.codekvast_server.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Thread-safe utility class for handling dates.
 *
 * @author Olle Hallin, olle.hallin@crisp.se
 */
public class DateUtils {

    private static final ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    private DateUtils() {
    }

    public static String formatDate(long timestampMillis) {
        return sdf.get().format(new Date(timestampMillis));
    }

    public static String getAge(long now, long timestampMillis) {
        if (timestampMillis == 0L) {
            return "";
        }

        long age = now - timestampMillis;

        long minutes = 60 * 1000L;
        if (age < 60 * minutes) {
            return String.format("%d min", age / minutes);
        }

        long hours = minutes * 60;
        if (age < 24 * hours) {
            return String.format("%d hours", age / hours);
        }
        long days = hours * 24;
        if (age < 30 * days) {
            return String.format("%d days", age / days);
        }

        long week = days * 7;
        return String.format("%d weeks", age / week);
    }


}
