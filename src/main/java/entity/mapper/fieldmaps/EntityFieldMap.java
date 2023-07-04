package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;
import org.apache.commons.lang.StringUtils;

/**
 * Defines a field map which maps a nested entity data
 */
public abstract class EntityFieldMap extends FieldMap {
    protected EntityMapper entityMapper;
    protected String foreignKey;
    protected String parentKey;
    protected Cascade cascade;

    public EntityFieldMap(
            String internalFieldName,
            String externalFieldName,
            String pluginName,
            boolean isAttribute,
            Access access,
            boolean exclusive,
            EntityMapper entityMapper,
            String foreignKey,
            String parentKey,
            Cascade cascade
    ) {
        super(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive);
        this.entityMapper = entityMapper;
        this.foreignKey = foreignKey;
        if (foreignKey != null && parentKey == null) {
            this.parentKey = foreignKey;
        } else {
            this.parentKey = parentKey;
        }
        this.cascade = cascade;

        if (entityMapper != null) {
            entityMapper.setParentFieldMap(this);
        }
    }

    public boolean isCascadeDelete() {
        return cascade == Cascade.DELETE || cascade == Cascade.ALL;
    }

    public EntityMapper getEntityMapper() {
        return entityMapper;
    }

    public String getForeignKey() {
        return foreignKey;
    }

    public String getParentKey() {
        return parentKey;
    }

    public boolean isJoin() {
        return StringUtils.isNotBlank(foreignKey);
    }

    public boolean isCascadeUpdate() {
        return cascade == Cascade.UPDATE || cascade == Cascade.ALL;
    }

    public enum Cascade {
        UPDATE,
        DELETE,
        ALL
    }
}
