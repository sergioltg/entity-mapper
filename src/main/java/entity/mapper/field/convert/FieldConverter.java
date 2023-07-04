package entity.mapper.field.convert;

/**
 * Provides conversion of field values between internal and external (interchange) formats.
 * Generic parameter T is the internal (data map) type of the field.
 */
public interface FieldConverter<T> {
    /**
     * Converts the specified field value from its internal format to its external format
     * @param fieldPath The path (period-separated) of the field being converted (may not be used)
     * @param internalValue The internal value of the field
     * @return The interchange format
     */
    Object toExternal(String fieldPath, T internalValue);

    /**
     * Converts the specified field value from its interchange format to its internal format
     * @param fieldPath The path (period-separated) of the field being converted (may not be used)
     * @param fieldType The target class of the converted object (its type in the data map)
     * @param externalValue The value of the field in its interchange format
     * @return The converted object
     */
    T toInternal(String fieldPath, Class<T> fieldType, Object externalValue);
}
