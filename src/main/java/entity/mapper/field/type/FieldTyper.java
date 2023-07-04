package entity.mapper.field.type;

/**
 * Obtains type information for fields.
 * This is primarily intended for parsing of interchange data.
 */
public interface FieldTyper {

    /**
     * Return the type for the field path.
     * @param fieldPath Field path with the delimiter '.'. For example : employeeContact.employeeName
     * @return Class type for the field, or null if the field is unknown
     */
    Class getFieldType(String fieldPath);

    /**
     * @param fieldPath Field path with the delimiter '.'. For example : employeeContact.employeeName
     * @return true if the field path has a field type false otherwise
     */
    boolean hasFieldType(String fieldPath);

    /**
     * @param fieldPath Field path with the delimiter '.'. For example : employeeContact.employeeName
     * @return true if the field path is that of a list to which field conversions should be applied
     */
    default boolean isConvertibleList(String fieldPath) {
        return false;
    }
}