package entity.mapper.field.type;

/**
 * Obtain field information for the localisable context
 */
public class LocalisableContextFieldTyper implements FieldTyper {

    @Override
    public Class getFieldType(String fieldPath) {
        if (fieldPath.endsWith("localisableContext.outlets") ||
                fieldPath.endsWith("localisableContext.customObjects") ||
                fieldPath.endsWith("localisableContext.accessibleOutlets") ||
                fieldPath.endsWith("localisableChildContext.outlets") ||
                fieldPath.endsWith("localisableChildContext.customObjects") ||
                fieldPath.endsWith("localisableChildContext.accessibleOutlets")) {
            return Integer.class;
        } else if (fieldPath.endsWith("localisableContext.universal") ||
                fieldPath.endsWith("localisableContext.restricted") ||
                fieldPath.endsWith("localisableChildContext.universal") ||
                fieldPath.endsWith("localisableChildContext.restricted")) {
            return Boolean.class;
        } else {
            return null;
        }
    }

    @Override
    public boolean hasFieldType(String fieldPath) {
        return getFieldType(fieldPath) != null;
    }
}
