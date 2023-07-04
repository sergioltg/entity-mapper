package entity.mapper.field.convert;

import org.apache.commons.beanutils.ConvertUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides conversion of field values of multiple types between their internal and interchange formats.
 * Individual field converters may be registered for specific types and/or field names.
 * <p>
 * Example Usage:
 * new MultiFieldConverter().
 * convert(BigDecimal.class). using(new BigDecimalConverter()).
 * convert("personType").     using(new PersonTypeConverter()).
 * convert(Date.class).       using(new DateFieldConverter());
 */
public class MultiFieldConverter implements FieldConverter {
    private final Map<Class, FieldConverter> fieldTypeConverters = new HashMap<>();
    private final Map<String, FieldConverter> fieldNameConverters = new HashMap<>();
    private FieldConverter defaultConverter = null;

    @Override
    public Object toExternal(String fieldPath, Object internalValue) {
        if (internalValue == null) {
            return null;
        }
        FieldConverter fieldConverter = findConverter(fieldPath, internalValue.getClass());
        if (fieldConverter != null) {
            return fieldConverter.toExternal(fieldPath, internalValue);
        }
        if (defaultConverter != null) {
            return defaultConverter.toExternal(fieldPath, internalValue);
        }
        return internalValue;
    }

    @Override
    public Object toInternal(String fieldPath, Class fieldType, Object externalValue) {
        FieldConverter fieldConverter = findConverter(fieldPath, fieldType);
        if (fieldConverter != null) {
            return fieldConverter.toInternal(fieldPath, fieldType, externalValue);
        }
        if (defaultConverter != null) {
            return defaultConverter.toInternal(fieldPath, fieldType, externalValue);
        }
        if (fieldType == String.class) {
            return externalValue;
        }
        // if the type is a number, and the value is null or an empty string, return null
        if (Number.class.isAssignableFrom(fieldType) && (externalValue == null || externalValue.equals(""))) {
            return null;
        }
        // If the field is a byte array, we expect a base-64 string as the external value
        if (externalValue instanceof String && fieldType.isArray() && fieldType.getComponentType().toString().equals("byte")) {
            return Base64.getDecoder().decode((String) externalValue);
        }
        return ConvertUtils.convert(externalValue, fieldType);
    }

    public interface Convert {
        MultiFieldConverter using(FieldConverter fieldConverter);
    }

    public Convert convert(Class fieldType) {
        return fieldConverter -> {
            fieldTypeConverters.put(fieldType, fieldConverter);
            return MultiFieldConverter.this;
        };
    }

    public Convert convert(String fieldPath) {
        return fieldConverter -> {
            fieldNameConverters.put(fieldPath, fieldConverter);
            return MultiFieldConverter.this;
        };
    }

    private FieldConverter findConverter(String fieldPath, Class fieldType) {
        FieldConverter fieldConverter = fieldNameConverters.get(fieldPath);
        if (fieldConverter != null) {
            return fieldConverter;
        }
        return fieldTypeConverters.get(fieldType);
    }

    public FieldConverter defaultTo(FieldConverter defaultConverter) {
        this.defaultConverter = defaultConverter;
        return this;
    }
}