package entity.mapper.field.convert;

import entity.mapper.datamap.DataMap;
import entity.mapper.datamap.DataMapEntry;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Converts a data map component to/from single csv string.
 * <p>
 * Each entry in the map is converted to a single string in the csv (and vice-versa) using the supplied mappers.
 * Entries and values can also be filtered using the supplied filters.
 */
public class ComponentToCsvFieldConverter implements FieldConverter<String> {
    private Predicate<Map.Entry<? extends String, ?>> entryFilter;
    private Function<Map.Entry<? extends String, ?>, String> entryMapper;
    private Set<String> defaultFields;
    private Function<String, DataMapEntry> defaultFieldMapper;
    private Predicate<String> csvValueFilter;
    private Function<String, DataMapEntry> csvValueMapper;

    public ComponentToCsvFieldConverter(
            Predicate<Map.Entry<? extends String, ?>> entryFilter,
            Function<Map.Entry<? extends String, ?>, String> entryMapper,
            Set<String> defaultFields,
            Function<String, DataMapEntry> defaultFieldMapper,
            Predicate<String> csvValueFilter,
            Function<String, DataMapEntry> csvValueMapper) {
        this.entryFilter = entryFilter;
        this.entryMapper = entryMapper;
        this.defaultFields = defaultFields;
        this.defaultFieldMapper = defaultFieldMapper;
        this.csvValueFilter = csvValueFilter;
        this.csvValueMapper = csvValueMapper;
    }

    public ComponentToCsvFieldConverter(Predicate<Map.Entry<? extends String, ?>> entryFilter, Function<Map.Entry<? extends String, ?>, String> entryMapper) {
        this.entryFilter = entryFilter;
        this.entryMapper = entryMapper;
        this.defaultFields = new HashSet<>();
        this.defaultFieldMapper = field -> new DataMapEntry(field, false);
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> new DataMapEntry(csvValue, true);
    }

    public ComponentToCsvFieldConverter(Function<Map.Entry<? extends String, ?>, String> entryMapper) {
        this.entryFilter = entry -> true;
        this.entryMapper = entryMapper;
        this.defaultFields = new HashSet<>();
        this.defaultFieldMapper = field -> new DataMapEntry(field, false);
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> new DataMapEntry(csvValue, true);
    }

    public ComponentToCsvFieldConverter() {
        this.entryFilter = entry -> true;
        this.entryMapper = entry -> (String) entry.getValue();
        this.defaultFields = new HashSet<>();
        this.defaultFieldMapper = field -> new DataMapEntry(field, false);
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> new DataMapEntry(csvValue, true);
    }

    @Override
    public String toInternal(String fieldPath, Class fieldType, Object externalValue) {
        return ((DataMap) externalValue).entrySet().stream()
                .filter(entryFilter)
                .map(entryMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
    }

    @Override
    public Object toExternal(String fieldPath, String internalValue) {
        DataMap external = defaultFields.stream()
                .map(defaultFieldMapper)
                .filter(entry -> entry.getKey() != null)
                .collect(DataMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), DataMap::putAll);
        Arrays.stream(internalValue.split(","))
                .filter(csvValueFilter)
                .map(csvValueMapper)
                .filter(entry -> entry.getKey() != null)
                .forEach(entry -> external.put(entry.getKey(), entry.getValue()));
        return external;
    }
}
