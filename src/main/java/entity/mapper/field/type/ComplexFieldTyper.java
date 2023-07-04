package entity.mapper.field.type;

import java.util.ArrayList;
import java.util.List;

/**
 * Obtains type information for fields from a registered list of field typers
 * <p>
 * Example Usage:
 * new ComplexFieldTyper().
 * use(new SimpleFieldTyper().
 * field("field1").       isa(String.class).
 * field("field2").       isa(Integer.class).
 * field("group1.field1").isa(Boolean.class)).
 * use(getEntityMapper()).
 * defaultTo(String.class);
 */
public class ComplexFieldTyper implements FieldTyper {
    private final List<FieldTyper> fieldTypers = new ArrayList<>();
    private Class defaultType = null;

    @Override
    public Class getFieldType(String fieldPath) {
        for (FieldTyper fieldTyper : fieldTypers) {
            Class fieldType = fieldTyper.getFieldType(fieldPath);
            if (fieldType != null) {
                return fieldType;
            }
        }
        return defaultType;
    }

    @Override
    public boolean hasFieldType(String fieldPath) {
        for (FieldTyper fieldTyper : fieldTypers) {
            if (fieldTyper.hasFieldType(fieldPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConvertibleList(String fieldPath) {
        for (FieldTyper fieldTyper : fieldTypers) {
            if (fieldTyper.isConvertibleList(fieldPath)) {
                return true;
            }
        }
        return false;
    }

    public ComplexFieldTyper use(FieldTyper fieldTyper) {
        fieldTypers.add(fieldTyper);
        return this;
    }

    public ComplexFieldTyper defaultTo(Class fieldType) {
        defaultType = fieldType;
        return this;
    }
}
