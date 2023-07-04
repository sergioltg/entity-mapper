package entity.mapper;

import entity.mapper.datamap.DataMap;
import entity.mapper.fieldmaps.ComponentMap;
import org.apache.commons.beanutils.PropertyUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ManyToOneJoinedEntityComponentDataMapping extends EntityComponentDataMapping {
    private Object parentEntityData;
    private AtomicBoolean modified;

    public ManyToOneJoinedEntityComponentDataMapping(ComponentMap fieldMap, DataMap componentDataMap, Object parentEntityData, AtomicBoolean modified) {
        super(fieldMap, componentDataMap);
        this.parentEntityData = parentEntityData;
        this.modified = modified;
    }

    @Override
    /*
      Sets a foreign key in a parent entity component to reference the saved component
     */
    public void associateComponent(Object savedComponentEntityData) {
        try {
            Object savingValue = PropertyUtils.getProperty(savedComponentEntityData, fieldMap.getForeignKey());
            if (modified != null) {
                if (!Objects.equals(PropertyUtils.getProperty(parentEntityData, fieldMap.getParentKey()), savingValue)) {
                    modified.set(true);
                }
            }
            PropertyUtils.setProperty(parentEntityData, fieldMap.getParentKey(), savingValue);
        } catch (Exception e) {
            throw new EntityMapperException(String.format("Error setting property '%s' in '%s'", fieldMap.getParentKey(), parentEntityData.getClass().getSimpleName()), e);
        }
    }
}
