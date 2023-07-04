package entity.mapper.field.convert;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class LongDateFieldConverter implements FieldConverter<Long> {
    public final static String DATETIME_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    public final static String DATE_ISO8601 = "yyyy-MM-dd";

    private List<DateFormat> dateFormats = new ArrayList<>();

    public LongDateFieldConverter() {
        this(DATETIME_ISO8601, DATE_ISO8601);
    }

    public LongDateFieldConverter(String mainFormat, String... otherFormats) {
        SimpleDateFormat sdf = new SimpleDateFormat(mainFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormats.add(sdf);
        for (String otherFormat : otherFormats) {
            sdf = new SimpleDateFormat(otherFormat);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            dateFormats.add(sdf);
        }
    }

    @Override
    public Object toExternal(String fieldPath, Long internalValue) {
        return dateFormats.get(0).format(new Date(internalValue));
    }

    @Override
    public Long toInternal(String fieldPath, Class<Long> fieldType, Object externalValue) {
        for (DateFormat dateFormat : dateFormats) {
            try {
                return dateFormat.parse((String) externalValue).getTime();
            } catch (Exception ignore) {
            }
        }

        return null;
    }
}
