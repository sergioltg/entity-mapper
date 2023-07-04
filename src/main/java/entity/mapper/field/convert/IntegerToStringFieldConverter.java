package entity.mapper.field.convert;

public class IntegerToStringFieldConverter implements FieldConverter<String> {

    public IntegerToStringFieldConverter() {
    }

    @Override
    public Object toExternal(String fieldPath, String internalValue) {
        try {
            return Integer.parseInt(internalValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toInternal(String fieldPath, Class<String> fieldType, Object externalValue) {
        return externalValue.toString();
    }
}
