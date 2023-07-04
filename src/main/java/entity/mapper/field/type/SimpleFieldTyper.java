package entity.mapper.field.type;

import java.util.HashMap;
import java.util.Map;

/**
 * Obtains type information for fields from a registered list.
 *
 * Example Usage:
 * new SimpleFieldTyper().
 *      field("field1").       isa(String.class).
 *      field("field2").       isa(Integer.class).
 *      field("group1.field1").isa(Boolean.class).
 *      defaultTo(String.class);
 *
 */
public class SimpleFieldTyper implements FieldTyper {
    private final Map<String, Class> fieldTypes = new HashMap<>();
    private Class defaultType = null;

    @Override
    public Class getFieldType(String fieldPath) {
        Class fieldType = fieldTypes.get(fieldPath);
        return fieldType == null ? defaultType : fieldType;
    }

    @Override
    public boolean hasFieldType(String fieldPath) {
        return fieldTypes.containsKey(fieldPath);
    }

    public interface Field {
        SimpleFieldTyper isa(Class fieldType);
    }

    public Field field(String fieldPath) {
        return fieldType -> {
            fieldTypes.put(fieldPath, fieldType);
            return SimpleFieldTyper.this;
        };
    }

    public SimpleFieldTyper defaultTo(Class fieldType) {
        defaultType = fieldType;
        return this;
    }
}
