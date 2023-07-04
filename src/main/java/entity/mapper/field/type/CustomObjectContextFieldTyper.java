package entity.mapper.field.type;

/**
 * Obtain field information for the custom object context
 *
 */
public class CustomObjectContextFieldTyper implements FieldTyper {

    @Override
    public Class getFieldType(String fieldPath) {
        if (fieldPath.endsWith("customObjectContext.customObjects")) {
            return Integer.class;
        }
        return null;
    }

    @Override
    public boolean hasFieldType(String fieldPath) {
        return getFieldType(fieldPath) != null;
    }
}
