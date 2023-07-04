package entity.mapper.field.convert;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Date field converter
 */
public class DateFieldConverter implements FieldConverter<Date> {
    public final static String DATETIME_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    public final static String DATE_ISO8601 = "yyyy-MM-dd";

    private DateFormat formattingDateFormat;
    private List<DateFormat> parsingDateFormats = new ArrayList<>();

    public DateFieldConverter() {
        this(false, false, DATETIME_ISO8601, DATE_ISO8601);
    }

    public DateFieldConverter(boolean formatAsLocalTime, boolean parseAsLocalTime) {
        this(formatAsLocalTime, parseAsLocalTime, DATETIME_ISO8601, DATE_ISO8601);
    }

    public DateFieldConverter(String mainFormat, String... otherFormats) {
        this(false, false, mainFormat, otherFormats);
    }

    public DateFieldConverter(boolean formatAsLocalTime, boolean parseAsLocalTime, String mainFormat, String... otherFormats) {
        formattingDateFormat = new SimpleDateFormat(mainFormat);
        if (!formatAsLocalTime) {
            formattingDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        DateFormat sdf = new SimpleDateFormat(mainFormat);
        if (!parseAsLocalTime) {
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        parsingDateFormats.add(sdf);
        for (String otherFormat : otherFormats) {
            sdf = new SimpleDateFormat(otherFormat);
            if (!parseAsLocalTime) {
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            parsingDateFormats.add(sdf);
        }
    }

    @Override
    public Object toExternal(String fieldPath, Date internalValue) {
        return formattingDateFormat.format(internalValue);
    }

    @Override
    public Date toInternal(String fieldPath, Class<Date> fieldType, Object externalValue) {
        for (DateFormat dateFormat : parsingDateFormats) {
            try {
                return dateFormat.parse((String) externalValue);
            } catch (Exception ignore) {
            }
        }

        return null;
    }
}
