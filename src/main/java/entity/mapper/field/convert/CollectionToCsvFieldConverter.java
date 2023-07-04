package entity.mapper.field.convert;

import entity.mapper.datamap.DataMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Converts a data map collection to/from single csv string.
 * <p>
 * Each entry in the collection is converted to a single string in the csv (and vice-versa) using the supplied mappers.
 * Entries and values can also be filtered using the supplied filters.
 */
public class CollectionToCsvFieldConverter<E> implements FieldConverter<String> {
    private Predicate<E> entryFilter;
    private Function<E, String> entryMapper;
    private Predicate<String> csvValueFilter;
    private Function<String, E> csvValueMapper;
    private boolean parenthesise;
    private boolean wrapInSimpleQuotes;

    public CollectionToCsvFieldConverter(Predicate<E> entryFilter, Function<E, String> entryMapper, Predicate<String> csvValueFilter, Function<String, E> csvValueMapper) {
        this.entryFilter = entryFilter;
        this.entryMapper = entryMapper;
        this.csvValueFilter = csvValueFilter;
        this.csvValueMapper = csvValueMapper;
    }

    public CollectionToCsvFieldConverter(Predicate<E> entryFilter, Function<E, String> entryMapper) {
        this.entryFilter = entryFilter;
        this.entryMapper = entryMapper;
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> (E) csvValue;
    }

    public CollectionToCsvFieldConverter(Function<E, String> entryMapper) {
        this.entryFilter = entry -> true;
        this.entryMapper = entryMapper;
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> (E) csvValue;
    }

    public CollectionToCsvFieldConverter() {
        this.entryFilter = entry -> true;
        this.entryMapper = Object::toString;
        this.csvValueFilter = csvValue -> true;
        this.csvValueMapper = csvValue -> (E) csvValue;
    }

    public CollectionToCsvFieldConverter(String componentFieldPath) {
        this(entry -> true, csvValue -> true, componentFieldPath, null);
    }

    public CollectionToCsvFieldConverter(String componentFieldPath, Function<DataMap, DataMap> csvValueProcessor) {
        this(entry -> entry != null, csvValue -> true, componentFieldPath, csvValueProcessor);
    }

    public CollectionToCsvFieldConverter(Predicate<E> entryFilter, String componentFieldPath) {
        this(entryFilter, csvValue -> true, componentFieldPath, null);
    }

    public CollectionToCsvFieldConverter(Predicate<E> entryFilter, Predicate<String> csvValueFilter, String componentFieldPath,
                                         Function<DataMap, DataMap> csvValueProcessor) {
        this.entryFilter = entryFilter;
        this.csvValueFilter = csvValueFilter;
        this.entryMapper = entry -> ((DataMap) entry).get(componentFieldPath).toString();
        this.csvValueMapper = csvValue -> {
            DataMap component = new DataMap();
            component.put(componentFieldPath, csvValue);
            if (csvValueProcessor != null) {
                return (E) csvValueProcessor.apply(component);
            }
            return (E) component;
        };
    }

    public CollectionToCsvFieldConverter setParenthesise(boolean parenthesise) {
        this.parenthesise = parenthesise;
        return this;
    }

    public CollectionToCsvFieldConverter setWrapInSingleQuotes(boolean wrapInSimpleQuotes) {
        this.wrapInSimpleQuotes = wrapInSimpleQuotes;
        return this;
    }


    @Override
    public String toInternal(String fieldPath, Class fieldType, Object externalValue) {
        Collection<E> collection = (Collection<E>) externalValue;
        if (collection.isEmpty()) {
            return null;
        }
        String csv = collection.stream()
                .filter(entryFilter)
                .map(entryMapper)
                .map(s -> wrapInSimpleQuotes ? "'" + s + "'" : s)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
        return parenthesise ? "(" + csv + ")" : csv;
    }

    @Override
    public Object toExternal(String fieldPath, String internalValue) {
        return Arrays.stream((internalValue.startsWith("(") ? internalValue.substring(1, internalValue.length() - 1)
                : internalValue).split(","))
                .map(s -> wrapInSimpleQuotes && s.startsWith("'") ? s.substring(1, s.length() - 1) : s)
                .filter(csvValueFilter)
                .map(csvValueMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
