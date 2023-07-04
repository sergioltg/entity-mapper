package entity.mapper.field.convert;

public class DefaultFieldConverter implements FieldConverter {
    @Override
    public Object toExternal(String fieldPath, Object internalValue) {
        return null;
    }

    @Override
    public Object toInternal(String fieldPath, Class fieldType, Object externalValue) {
        return null;
    }
}
