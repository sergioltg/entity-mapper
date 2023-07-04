package entity.mapper;

import entity.mapper.datamap.DataMap;
import entity.mapper.fieldmaps.ComponentMap;
import org.apache.commons.beanutils.PropertyUtils;

public class OneToManyJoinedEntityComponentDataMapping extends EntityComponentDataMapping {
    public OneToManyJoinedEntityComponentDataMapping(ComponentMap fieldMap, DataMap componentDataMap) {
        super(fieldMap, componentDataMap);
    }

    @Override
    /*
      Sets the foreign key in a joined entity component to reference the parent key value
     */
    public void associateComponent(Object savedParentEntityData) {
        String parentKeyPropertyName = getAssociationKeyPropertyName();
        try {
            componentDataMap.put(fieldMap.getForeignKey(), PropertyUtils.getProperty(savedParentEntityData, parentKeyPropertyName));
        } catch (Exception e) {
            throw new EntityMapperException(String.format("Error getting value of property '%s' from '%s' as foreign key", parentKeyPropertyName, savedParentEntityData.getClass().getSimpleName()), e);
        }
    }

    public String getAssociationKeyPropertyName() {
        return fieldMap.getParentEntityMapper().findByExternalFieldName(fieldMap.getParentKey()).getInternalFieldName();
    }
}
