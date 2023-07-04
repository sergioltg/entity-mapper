package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;

import java.util.Collection;
import java.util.Collections;

/**
 * Defines a component mapping.
 */
public class ComponentMap extends EntityFieldMap {
    protected boolean outerJoin;
    protected boolean forSubclass;
    protected boolean flattened;

    private ComponentMap(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            EntityMapper entityMapper,
            Access access,
            boolean exclusive,
            boolean outerJoin,
            boolean forSubclass,
            boolean flattened,
            String foreignKey,
            String parentKey,
            Cascade cascade) {
        super(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive, entityMapper, foreignKey, parentKey, cascade);
        this.outerJoin = outerJoin;
        this.forSubclass = forSubclass;
        this.flattened = flattened;

        // change the version field name to add a prefix to avoid clashing with the fields from the parent
        if (flattened) {
            FieldMap versionFieldMap = entityMapper.findByExternalFieldName(EntityMapper.VERSION_KEY);
            if (versionFieldMap != null) {
                versionFieldMap.externalFieldName = externalFieldName + "_" + EntityMapper.VERSION_KEY;
            }
        }
    }

    public boolean isOuterJoin() {
        return outerJoin;
    }

    public boolean isForSubclass() {
        return forSubclass;
    }

    public boolean isFlattened() {
        return flattened;
    }

    public int getFieldMapCount(boolean includeJoins) {
        int count = 0;
        for (FieldMap fieldMap : (Collection<FieldMap>) entityMapper.getFieldMaps()) {
            if (fieldMap instanceof SimpleFieldMap) {
                count++;
            } else if (fieldMap instanceof ComponentMap && (includeJoins || !((ComponentMap) fieldMap).isJoin())) {
                count += ((ComponentMap) fieldMap).getFieldMapCount(includeJoins);
            }
        }
        return count;
    }

    public boolean hasDefaults() {
        for (FieldMap fieldMap : (Collection<FieldMap>) entityMapper.getFieldMaps()) {
            if (fieldMap instanceof SimpleFieldMap) {
                if (((SimpleFieldMap) fieldMap).getDefaultValue() != null) {
                    return true;
                }
            } if (fieldMap instanceof ComponentMap) {
                if (((ComponentMap) fieldMap).hasDefaults()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentMap)) return false;

        ComponentMap componentMap = (ComponentMap) o;

        if (internalFieldName != null ? !internalFieldName.equals(componentMap.internalFieldName) : componentMap.internalFieldName != null)
            return false;
        if (externalFieldName != null ? !externalFieldName.equals(componentMap.externalFieldName) : componentMap.externalFieldName != null)
            return false;
        return entityMapper != null ? entityMapper.equals(componentMap.entityMapper) : componentMap.entityMapper == null;

    }

    @Override
    public int hashCode() {
        int result = internalFieldName != null ? internalFieldName.hashCode() : 0;
        result = 31 * result + (externalFieldName != null ? externalFieldName.hashCode() : 0);
        result = 31 * result + (entityMapper != null ? entityMapper.hashCode() : 0);
        return result;
    }

    /**
     * Return a field map provider that will provide a ComponentMap
     *
     * @param internalFieldName Internal name
     * @param entityMapper      Mapper to define the item in the component
     * @return
     */
    public static Provider component(
            String internalFieldName,
            EntityMapper entityMapper
    ) {
        return new Provider(new ComponentMap(internalFieldName, internalFieldName, null, false, entityMapper, Access.FULL, false, false, false, false, null, null, null));
    }

    /**
     * Return a field map provider that will provide a ComponentMap
     *
     * @param internalFieldName Internal name
     * @param pluginName        Plugin name (attribute fields)
     * @param externalFieldName External name
     * @param entityMapper      Mapper to define the items in the component
     * @param access            FULL, READONLY, or FINAL
     * @param outerJoin         Indicates if this component requires an outer join (optional component)
     * @param foreignKey        Used to specify an explicit join
     * @param parentKey         Used to specify an explicit join
     * @param cascade           Indicates cascade mode for this component
     * @return
     */
    public static Provider component(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            EntityMapper entityMapper,
            Access access,
            boolean exclusive,
            boolean outerJoin,
            boolean flattened,
            String foreignKey,
            String parentKey,
            Cascade cascade
    ) {
        return new Provider(new ComponentMap(internalFieldName, externalFieldName, pluginName, isAttribute, entityMapper, access, exclusive,
                outerJoin, false, flattened, foreignKey, parentKey, cascade));
    }

    /**
     * Return a field map provider that will provide a ComponentMap
     *
     * @param internalFieldName Internal name
     * @param pluginName        Plugin name (attribute fields)
     * @param externalFieldName External name
     * @param entityMapper      Mapper to define the items in the component
     * @param access            FULL, READONLY, or FINAL
     * @return
     */
    public static Provider subclassComponent(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            EntityMapper entityMapper,
            Access access,
            boolean exclusive
    ) {
        return new Provider(new ComponentMap(internalFieldName, externalFieldName, pluginName, isAttribute, entityMapper, access,
                exclusive, false, true, false, null, null, null));
    }

    protected static class Provider implements FieldMapProvider {
        private ComponentMap componentMap;

        public Provider(ComponentMap componentMap) {
            this.componentMap = componentMap;
        }

        @Override
        public Collection<? extends FieldMap> getFieldMaps() {
            return Collections.singletonList(componentMap);
        }
    }
}