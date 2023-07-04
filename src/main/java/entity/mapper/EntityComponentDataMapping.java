package entity.mapper;

import entity.mapper.datamap.DataMap;
import entity.mapper.fieldmaps.ComponentMap;

public abstract class EntityComponentDataMapping implements EntityDataMapping {
    protected ComponentMap fieldMap;
    protected DataMap componentDataMap;

    public EntityComponentDataMapping(ComponentMap fieldMap, DataMap componentDataMap) {
        this.fieldMap = fieldMap;
        this.componentDataMap = componentDataMap;
    }

    public ComponentMap getFieldMap() {
        return fieldMap;
    }

    public DataMap getComponentDataMap() {
        return componentDataMap;
    }

    public abstract void associateComponent(Object savedEntityData);

    public String getAssociationKeyPropertyName() {
        return null;
    }
}
