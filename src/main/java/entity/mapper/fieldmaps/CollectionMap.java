package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;
import entity.mapper.metadata.ClassContext;
import entity.mapper.metadata.FieldMetaData;
import entity.mapper.metadata.FieldMetaDataList;
import entity.mapper.metadata.ReflectorFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Defines a collection mapping that can be EAGER or LAZY loaded.
 */
public class CollectionMap extends EntityFieldMap {
    protected Mode collectionMode;
    protected Class primitiveType;

    public enum Mode {
        EAGER, LAZY
    }

    protected CollectionMap(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            Mode collectionMode,
            Access access,
            boolean exclusive,
            EntityMapper entityMapper,
            Class primitiveType,
            String foreignKey,
            String parentKey,
            Cascade cascade
    ) {
        super(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive, entityMapper, foreignKey, parentKey, cascade);
        this.collectionMode = collectionMode;
        this.primitiveType = primitiveType;
    }

    /**
     * Returns Collection mode as Enumeration which can be LAZY or EAGER. The Eager will results in returning all data
     * while lazy won't return relevant collection.
     *
     * @return
     */
    public Mode getCollectionMode() {
        return collectionMode;
    }

    public Class getPrimitiveType() {
        return primitiveType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollectionMap)) return false;

        CollectionMap that = (CollectionMap) o;

        if (internalFieldName != null ? !internalFieldName.equals(that.internalFieldName) : that.internalFieldName != null)
            return false;
        if (externalFieldName != null ? !externalFieldName.equals(that.externalFieldName) : that.externalFieldName != null)
            return false;
        if (collectionMode != that.collectionMode) return false;
        if (access != that.access) return false;
        return entityMapper != null ? entityMapper.equals(that.entityMapper) : that.entityMapper == null;
    }

    @Override
    public int hashCode() {
        int result = internalFieldName != null ? internalFieldName.hashCode() : 0;
        result = 31 * result + (externalFieldName != null ? externalFieldName.hashCode() : 0);
        result = 31 * result + (collectionMode != null ? collectionMode.hashCode() : 0);
        result = 31 * result + (access != null ? access.hashCode() : 0);
        result = 31 * result + (entityMapper != null ? entityMapper.hashCode() : 0);
        return result;
    }

    /**
     * Return a field map provider that will provide a CollectionMap
     *
     * @param internalFieldName Internal name
     * @param collectionMode    EAGER or LAZY
     * @param entityMapper      Mapper to define the items in the collection
     * @return
     */
    public static Provider collection(
            String internalFieldName,
            Mode collectionMode,
            EntityMapper entityMapper
    ) {
        return new Provider(new CollectionMap(internalFieldName, internalFieldName, null, false, collectionMode, Access.FULL, false, entityMapper, null, null, null, null));
    }

    /**
     * Return a field map provider that will provide a CollectionMap
     *
     * @param internalFieldName Internal name
     * @param collectionMode    EAGER or LAZY
     * @param primitiveType     Primitive type for primitive collections
     * @return
     */
    public static Provider collection(
            String internalFieldName,
            Mode collectionMode,
            Class primitiveType
    ) {
        return new Provider(new CollectionMap(internalFieldName, internalFieldName, null, false, collectionMode, Access.FULL, false, null, primitiveType, null, null, null));
    }

    /**
     * Return a field map provider that will provide a CollectionMap
     *
     * @param internalFieldName Internal name
     * @param externalFieldName External name
     * @param pluginName        Plugin name (attribute fields)
     * @param collectionMode    EAGER or LAZY
     * @param access            FULL, READONLY, or FINAL
     * @param entityMapper      Mapper to define the items in the collection
     * @param primitiveType     For collections of primitives
     * @param foreignKey        Used to specify an explicit join
     * @param parentKey         Used to specify an explicit join
     * @param cascade           Indicates cascade mode for this collection
     * @return
     */
    public static Provider collection(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            Mode collectionMode,
            Access access,
            boolean exclusive,
            EntityMapper entityMapper,
            Class primitiveType,
            String foreignKey,
            String parentKey,
            Cascade cascade
    ) {
        return new Provider(new CollectionMap(internalFieldName, externalFieldName, pluginName, isAttribute, collectionMode, access, exclusive,
                entityMapper, primitiveType, foreignKey, parentKey, cascade));
    }

    private static class Provider implements FieldMapProvider {
        private CollectionMap collectionMap;

        public Provider(CollectionMap collectionMap) {
            this.collectionMap = collectionMap;
        }

        @Override
        public Collection<? extends FieldMap> getFieldMaps() {
            // In case it is null doesn't return the collectionMap as it hasn't loaded.
            if (collectionMap.getEntityMapper() == null && collectionMap.getPrimitiveType() == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(collectionMap);
        }
    }

    public boolean isOneToMany() {
        ReflectorFactory reflectorFactory = ReflectorFactory.getInstance();
        FieldMetaDataList metaDataList = reflectorFactory.getReflector().getFieldListForClass(new ClassContext("", parentEntityMapper.getBaseClass()));
        if (metaDataList != null) {
            FieldMetaData fieldMetaData = metaDataList.getByInternalName(internalFieldName);
            return fieldMetaData != null && fieldMetaData.getAttributes().contains(FieldMetaData.Attribute.ONE_TO_MANY);
        }

        return false;
    }

    public boolean isDeleteOrphan() {
        ReflectorFactory reflectorFactory = ReflectorFactory.getInstance();
        FieldMetaDataList metaDataList = reflectorFactory.getReflector().getFieldListForClass(new ClassContext("", parentEntityMapper.getBaseClass()));
        if (metaDataList != null) {
            FieldMetaData fieldMetaData = metaDataList.getByInternalName(internalFieldName);
            return fieldMetaData != null && fieldMetaData.getAttributes().contains(FieldMetaData.Attribute.ORPHAN_DELETE);
        }

        return false;
    }
}