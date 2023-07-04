package entity.mapper.fieldmaps;

import entity.mapper.metadata.ClassContext;
import entity.mapper.metadata.FieldMetaData;
import entity.mapper.metadata.FieldMetaDataList;
import entity.mapper.metadata.ReflectorFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Map to define a simple field
 *
 */
public class SimpleFieldMap extends FieldMap {
    private boolean identifier;
    private boolean entityName;
    private Object defaultValue;

    protected SimpleFieldMap(String internalFieldName, String externalFieldName) {
        this(internalFieldName, externalFieldName, null, false, false, false, Access.FULL, false, null);
    }

    public SimpleFieldMap(String internalFieldName, String externalFieldName, String pluginName, boolean isAttribute, boolean identifier, boolean entityName, Access access, boolean exclusive, Object defaultValue) {
        super(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive);
        this.identifier = identifier;
        this.entityName = entityName;
        this.defaultValue = defaultValue;
    }

    public boolean isIdentifier() {
        return identifier;
    }

    public boolean isEntityName() {
        return entityName;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static FieldMapProvider fieldMap(String internalFieldName, String externalFieldName, String pluginName, boolean isAttribute, boolean identifier, boolean entityName, Access access, boolean exclusive, Object defaultValue) {
        return new Provider(Arrays.asList(new SimpleFieldMap(internalFieldName, externalFieldName, pluginName, isAttribute, identifier, entityName, access, exclusive, defaultValue)));
    }

    public static FieldMapProvider fields(String... fields) {
        return new Provider(Arrays.stream(fields).map(s -> new SimpleFieldMap(s, s)).collect(Collectors.toList()));
    }

    public static FieldMapProvider fieldMap(String internalFieldName, String externalFieldName) {
        return new Provider(Arrays.asList(new SimpleFieldMap(internalFieldName, externalFieldName)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleFieldMap)) return false;

        SimpleFieldMap that = (SimpleFieldMap) o;

        if (internalFieldName != null ? !internalFieldName.equals(that.internalFieldName) : that.internalFieldName != null)
            return false;
        return externalFieldName != null ? externalFieldName.equals(that.externalFieldName) : that.externalFieldName == null;
    }

    @Override
    public int hashCode() {
        int result = internalFieldName != null ? internalFieldName.hashCode() : 0;
        result = 31 * result + (externalFieldName != null ? externalFieldName.hashCode() : 0);
        return result;
    }

    private static class Provider implements FieldMapProvider {

        private Collection<SimpleFieldMap> simpleFieldMaps;

        public Provider(List<SimpleFieldMap> simpleFieldMaps) {
            this.simpleFieldMaps = simpleFieldMaps;
        }

        @Override
        public Collection<? extends FieldMap> getFieldMaps() {
            return simpleFieldMaps;
        }
    }

    @Override
    public boolean isExclusive() {
        if (super.isExclusive()) {
            return true;
        }

        // Identifiers fields in a one-to-many component are implicitly exclusive
        ReflectorFactory reflectorFactory = ReflectorFactory.getInstance();
        if (identifier && parentEntityMapper.getParentFieldMap() != null) {
            FieldMetaDataList metaDataList = reflectorFactory.getReflector().getFieldListForClass(new ClassContext("", parentEntityMapper.getParentFieldMap().getParentEntityMapper().getBaseClass()));
            if (metaDataList != null) {
                FieldMetaData fieldMetaData = metaDataList.getByInternalName(parentEntityMapper.getParentFieldMap().getInternalFieldName());
                return fieldMetaData != null && fieldMetaData.getAttributes().contains(FieldMetaData.Attribute.ONE_TO_MANY);
            }
        }

        return false;
    }
}