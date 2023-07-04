package entity.mapper.field.convert;

import entity.mapper.datamap.DataMap;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts a serialized property map to a DataMap containing fields.
 */
public class PropertyMapFieldConverter implements FieldConverter<Map<String, Object>> {
    private final Map<String, String> propertyNameToFieldNameMap;
    private final Map<String, String> fieldNameToPropertyNameMap;

    public PropertyMapFieldConverter(Map<String, String> propertyNameToFieldNameMap) {
        this.propertyNameToFieldNameMap = propertyNameToFieldNameMap;
        fieldNameToPropertyNameMap = propertyNameToFieldNameMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    @Override
    public Map<String, Object> toInternal(String fieldPath, Class fieldType, Object externalValue) {
        return ((DataMap) externalValue).entrySet().stream()
                .filter(entry -> fieldNameToPropertyNameMap.containsKey(entry.getKey()))
                .collect(HashMap::new, (map, entry) -> map.put(fieldNameToPropertyNameMap.get(entry.getKey()), entry.getValue()), Map::putAll);
    }

    @Override
    public Object toExternal(String fieldPath, Map<String, Object> internalValue) {
        return internalValue.entrySet().stream()
                .filter(entry -> propertyNameToFieldNameMap.containsKey(entry.getKey()))
                .collect(DataMap::new, (map, entry) -> map.put(propertyNameToFieldNameMap.get(entry.getKey()), entry.getValue()), DataMap::putAll);
    }
}
