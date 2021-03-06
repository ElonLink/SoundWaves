package org.bottiger.podcast.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by aplb on 19-09-2015.
 */
public class DateUtils {

    private static final Map<String, String> DATE_FORMAT_REGEXPS = new HashMap<String, String>() {{
        put("^\\d{8}$", "yyyyMMdd");
        put("^\\d{1,2}-\\d{1,2}-\\d{4}$", "dd-MM-yyyy");
        put("^\\d{4}-\\d{1,2}-\\d{1,2}$", "yyyy-MM-dd");
        put("^\\d{1,2}/\\d{1,2}/\\d{4}$", "MM/dd/yyyy");
        put("^\\d{4}/\\d{1,2}/\\d{1,2}$", "yyyy/MM/dd");
        put("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}$", "dd MMM yyyy");
        put("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}$", "dd MMMM yyyy");
        put("^\\d{12}$", "yyyyMMddHHmm");
        put("^\\d{8}\\s\\d{4}$", "yyyyMMdd HHmm");
        put("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}$", "dd-MM-yyyy HH:mm");
        put("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}$", "yyyy-MM-dd HH:mm");
        put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}$", "MM/dd/yyyy HH:mm");
        put("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}$", "yyyy/MM/dd HH:mm");
        put("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}$", "dd MMM yyyy HH:mm");
        put("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}$", "dd MMMM yyyy HH:mm");
        put("^\\d{14}$", "yyyyMMddHHmmss");
        put("^\\d{8}\\s\\d{6}$", "yyyyMMdd HHmmss");
        put("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd-MM-yyyy HH:mm:ss");
        put("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$", "yyyy-MM-dd HH:mm:ss");
        put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "MM/dd/yyyy HH:mm:ss");
        put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2} [PMApma]{2}$", "MM/dd/yyyy HH:mm:ss a"); // am/pm
        put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2} [PMApma]{2}$", "MM/dd/yyyy HH:mm a"); // am/pm
        put("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$", "yyyy/MM/dd HH:mm:ss");
        put("^\\d{1,2}\\s[A-Za-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd MMM yyyy HH:mm:ss");
        put("^\\d{1,2}\\s[A-Za-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd MMMM yyyy HH:mm:ss");
        put("^\\d{1,2}\\s[A-Za-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[0-9+-]+$", "dd MMM yyyy HH:mm:ss Z");
        put("^\\d{1,2}\\s[A-Za-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[A-Za-z0-9+-]+$", "dd MMMM yyyy HH:mm:ss z");
        put("^[A-Za-z]{3}\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[0-9+-]+$", "EEE dd MMM yyyy HH:mm:ss Z"); // missing ,
        put("^[A-Za-z]{3}\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[A-Za-z0-9+-]+$", "EEE dd MMM yyyy HH:mm:ss z"); // missing ,
        put("^[A-Za-z]{3},\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}\\s[0-9+-]+$", "EEE, dd MMM yyyy HH:mm Z"); // no seconds
        put("^[A-Za-z]{3},\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}\\s[A-Za-z0-9+-]+$", "EEE, dd MMM yyyy HH:mm z"); // no seconds
        put("^[A-Za-z]{3},\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[0-9+-]+$", "EEE, dd MMM yyyy HH:mm:ss Z");
        put("^[A-Za-z]{3},\\s\\d{1,2}\\s[A-Za-z]{3,9}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[A-Za-z0-9+-]+$", "EEE, dd MMM yyyy HH:mm:ss z");
    }};

    private static final Map<String, String> UNSUPPORTED_TIME_ZONE = new HashMap<String, String>() {{
        put("BST", "GMT+1");
        put("PST", "-0800");
        put("PDT", "GMT-7");
        put("EST", "GMT-4");
    }};

    private static String[] sDateFormatKeys = null;

    private static String sSimpleDateFormatFormat = null;
    private static SimpleDateFormat sSimpleDateFormatCache = null;

    public interface Hint {
        String get();
    }

    @Nullable
    public static java.util.Date parseRFC3339Date(String datestring) throws java.text.ParseException, IndexOutOfBoundsException{

        if (TextUtils.isEmpty(datestring))
            return null;

        Date d = new Date();

        // Some feed format the date as: Wed, 09 Sep 2015 12:17:03 PDT
        //
        // http://stackoverflow.com/questions/1223052/how-do-i-find-out-if-first-character-of-a-string-is-a-number
        if (!Character.isDigit(datestring.charAt(0))) {
            try{
                SimpleDateFormat s = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                d = s.parse(datestring);
            }
            catch(java.text.ParseException pe){//try again with optional decimals
                SimpleDateFormat s = new SimpleDateFormat("EEE, dd MMMM yyyy HH:mm:ss Z");
                s.setLenient(true);
                d = s.parse(datestring);
            }
            return d;
        }

        //if there is no time zone, we don't need to do any special parsing.
        if(datestring.endsWith("Z")){
            try{
                SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");//spec for RFC3339
                d = s.parse(datestring);
            }
            catch(java.text.ParseException pe){//try again with optional decimals
                SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");//spec for RFC3339 (with fractional seconds)
                s.setLenient(true);
                d = s.parse(datestring);
            }
            return d;
        }

        //step one, split off the timezone.
        String firstpart = datestring.substring(0,datestring.lastIndexOf('-'));
        String secondpart = datestring.substring(datestring.lastIndexOf('-'));

        //step two, remove the colon from the timezone offset
        if (secondpart.contains(":"))
            secondpart = secondpart.substring(0,secondpart.indexOf(':')) + secondpart.substring(secondpart.indexOf(':')+1);

        datestring  = firstpart + secondpart;
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");//spec for RFC3339
        try{
            d = s.parse(datestring);
        }
        catch(java.text.ParseException pe){//try again with optional decimals
            s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");//spec for RFC3339 (with fractional seconds)
            s.setLenient(true);
            d = s.parse(datestring);
        }
        return d;
    }

    // Converters ---------------------------------------------------------------------------------

    /**
     * Parse the given date string to date object and return a date instance based on the given
     * date string. This makes use of the {@link DateUtil#determineDateFormat(String)} to determine
     * the SimpleDateFormat pattern to be used for parsing.
     *
     * @param dateString The date string to be parsed to date object.
     * @return The parsed date object.
     * @throws ParseException If the date format pattern of the given date string is unknown, or if
     * the given date string or its actual date is invalid based on the date format pattern.
     */
    public static Pair<Date, Hint> parse(@NonNull String dateString, @Nullable Hint argHint) throws ParseException {
        Pair<String, Hint> dateFormat = determineDateFormat(dateString, argHint);
        if (dateFormat == null) {
            throw new ParseException("Unknown date format: " + dateString, 0);
        }

        Date returnDate = parse(dateString, dateFormat.first);
        return new Pair<>(returnDate, dateFormat.second);
    }

    /**
     * Validate the actual date of the given date string based on the given date format pattern and
     * return a date instance based on the given date string.
     *
     * @param dateString The date string.
     * @param dateFormat The date format pattern which should respect the SimpleDateFormat rules.
     * @return The parsed date object.
     * @throws ParseException If the given date string or its actual date is invalid based on the
     * given date format pattern.
     * @see SimpleDateFormat
     */
    public static synchronized Date parse(@NonNull String dateString, @NonNull String dateFormat) throws ParseException {

        // This is a hack to deal with date formats not known to Java
        Iterator it = UNSUPPORTED_TIME_ZONE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            dateString = dateString.replace(pair.getKey().toString(), pair.getValue().toString());
            it.remove(); // avoids a ConcurrentModificationException
        }

        SimpleDateFormat simpleDateFormat;
        if (sSimpleDateFormatCache != null && dateFormat.equals(sSimpleDateFormatFormat)) {
            simpleDateFormat = sSimpleDateFormatCache;
        } else {
            // http://blog.andromo.com/2011/simpledateformat-can-be-slow/
            //simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
            simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.getDefault());
            simpleDateFormat.setLenient(false); // Don't automatically convert invalid date.

            sSimpleDateFormatFormat = dateFormat;
            sSimpleDateFormatCache = simpleDateFormat;
        }

        return simpleDateFormat.parse(dateString);
    }

    // Validators ---------------------------------------------------------------------------------

    /**
     * Checks whether the actual date of the given date string is valid. This makes use of the
     * {@link DateUtil#determineDateFormat(String)} to determine the SimpleDateFormat pattern to be
     * used for parsing.
     * @param dateString The date string.
     * @return True if the actual date of the given date string is valid.
     */
    public static boolean isValidDate(String dateString) {
        try {
            parse(dateString, getHint(null));
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Checks whether the actual date of the given date string is valid based on the given date
     * format pattern.
     * @param dateString The date string.
     * @param dateFormat The date format pattern which should respect the SimpleDateFormat rules.
     * @return True if the actual date of the given date string is valid based on the given date
     * format pattern.
     * @see SimpleDateFormat
     */
    public static boolean isValidDate(String dateString, String dateFormat) {
        try {
            parse(dateString, dateFormat);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    // Checkers -----------------------------------------------------------------------------------

    /**
     * Determine SimpleDateFormat pattern matching with the given date string. Returns null if
     * format is unknown. You can simply extend DateUtil with more formats if needed.
     *
     * Optimization: This method has proven to be very slow. Therefore we return a Pair where the
     * second value it a hint of the format for caching higher up.
     *
     * @param dateString The date string to determine the SimpleDateFormat pattern for.
     * @return The matching SimpleDateFormat pattern, or null if format is unknown.
     * @see SimpleDateFormat
     */
    @Nullable
    private static Pair<String, Hint> determineDateFormat(String dateString, @Nullable Hint argKeyHint) {

        String dateStringLowerCase = dateString.toLowerCase();

        // Test the hint first
        if (argKeyHint != null) {
            if (dateStringLowerCase.matches(argKeyHint.get())) {
                return new Pair<>(DATE_FORMAT_REGEXPS.get(argKeyHint.get()), argKeyHint);
            }
        }

        for (final String regexp : getDateFormatKeys()) {

            if (argKeyHint != null && regexp.equals(argKeyHint.get()))
                continue;

            if (dateStringLowerCase.matches(regexp)) {
                return new Pair<>(DATE_FORMAT_REGEXPS.get(regexp), getHint(regexp));
            }
        }
        return null; // Unknown format.
    }

    @Nullable
    private static Hint getHint(@Nullable final String argKey) {
        if (argKey == null)
            return null;

        return new Hint() {
            @Override
            public String get() {
                return argKey;
            }
        };
    }

    public static Date preventDateInTheFutre(@NonNull Date argDate) {
        Date now = new Date();
        Date newDate = argDate.before(now) ? argDate : now;

        return newDate;
    }

    private static synchronized String[] getDateFormatKeys() {
        if (sDateFormatKeys == null) {
            Set<String> keySet = DATE_FORMAT_REGEXPS.keySet();
            sDateFormatKeys = keySet.toArray(new String[keySet.size()]);
            Collections.reverse(Arrays.asList(sDateFormatKeys));
        }

        return sDateFormatKeys;
    }
}
