package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Defines a collection mapping that can be EAGER or LAZY loaded.
 */
public class MergedCollectionMap extends CollectionMap {
    private String indexedByFieldName;

    private MergedCollectionMap(
            String externalFieldName,
            Mode collectionMode,
            Access access,
            boolean exclusive,
            String indexedByFieldName,
            EntityMapper entityMapper
    ) {
        super(externalFieldName, externalFieldName, null, false, collectionMode, access, exclusive, entityMapper, null, null, null, null);
        this.indexedByFieldName = indexedByFieldName;

        // Make all fields of child collection maps visible on this map's entity mapper.
        // This is to allow resolution of lazy-loading paths which will not be aware of the child collection maps.
        List<FieldMap> childMaps = new ArrayList<>(entityMapper.getFieldMaps());
        for (FieldMap childMap : childMaps) {
            if (childMap instanceof CollectionMap) {
                CollectionMap childCollectionMap = (CollectionMap) childMap;
                entityMapper.merge(childCollectionMap.getEntityMapper());
            }
        }
    }

    /**
     * Return a field map provider that will provide a MergedCollectionMap
     *
     * @param externalFieldName External name
     * @param collectionMode    EAGER or LAZY
     * @param access            FULL, READONLY, or FINAL
     * @param customMapHandler  Handler to perform custom operations on merged collection
     * @param entityMapper      Mapper to define the merged collections
     * @return
     */
    public static Provider mergedCollection(
            String externalFieldName,
            Mode collectionMode,
            Access access,
            boolean exclusive,
            String indexedByFieldName,
            EntityMapper entityMapper
    ) {
        return new Provider(new MergedCollectionMap(externalFieldName, collectionMode, access, exclusive, indexedByFieldName, entityMapper));
    }

    public String getIndexedByFieldName() {
        return indexedByFieldName;
    }

    private static class Provider implements FieldMapProvider {
        private MergedCollectionMap mergedCollectionMap;

        public Provider(MergedCollectionMap mergedCollectionMap) {
            this.mergedCollectionMap = mergedCollectionMap;
        }

        @Override
        public Collection<? extends FieldMap> getFieldMaps() {
            return Collections.singletonList(mergedCollectionMap);
        }
    }
}