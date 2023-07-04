package entity.mapper.handlers;

import entity.mapper.*;
import entity.mapper.datamap.DataMap;
import entity.mapper.field.convert.FieldConverter;
import entity.mapper.fieldmaps.ComponentMap;
import entity.session.SessionContext;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles a ComponentMap conversion.
 */
public class ComponentMapHandler implements MapHandler<ComponentMap> {

    @Override
    public void handleMapToObject(ComponentMap fieldMap, Object value, Object target, DataMap parentDataMap, EntityMapper.Operation operation,
                                  Collection<EntityDataMapping> preemptedEntityDataMappings,
                                  Collection<EntityDataMapping> deferredEntityDataMappings, FieldConverter fieldConverter, AtomicBoolean modified) throws Exception {
        if (value != null && !(value instanceof Map)) {
            throw new IllegalArgumentException("Value should be a Map");
        }
        if (!fieldMap.operationAllowed(operation)) {
            return;
        }
        if (fieldMap.isAttribute()) {
            if (!(target instanceof Attributed)) {
                return;
            }
        } else if (!fieldMap.isJoin() && !fieldMap.isForSubclass() && !PropertyUtils.isReadable(target, fieldMap.getInternalFieldName())) {
            return;
        }

        // Ignore non-updatable joins
        if (!fieldMap.isAttribute() && fieldMap.isJoin() && !fieldMap.isCascadeUpdate()) {
            return;
        }

        // Handle null values
        if (value == null) {
            if (fieldMap.isAttribute()) {
                if (fieldMap.hasDefaults()) {
                    value = new DataMap();
                } else {
                    ((Attributed) target).setAttribute(fieldMap.getInternalFieldName(), null);
                    return;
                }
            } else if (!fieldMap.isJoin()) {

                // If the field map has default values and is not marked optional, populate from any empty map
                if (fieldMap.hasDefaults() && !fieldMap.isOuterJoin()) {
                    value = new DataMap();
                } else {
                    if (!fieldMap.isForSubclass()) {
                        if (modified != null) {
                            if (PropertyUtils.getProperty(target, fieldMap.getInternalFieldName()) != null) {
                                modified.set(true);
                            }
                        }
                        PropertyUtils.setProperty(target, fieldMap.getInternalFieldName(), null);
                    }
                    return;
                }
            }
        }

        DataMap dataMap = (DataMap) value;
        if (dataMap != null) {
            dataMap.setParent(parentDataMap);
        }
        Object currentValue;

        if (fieldMap.isAttribute()) {
            currentValue = ((Attributed) target).getAttribute(fieldMap.getInternalFieldName());
        } else {

            // For a joined component referencing the parent primary key (one to many), defer the update
            if (fieldMap.isJoin() && fieldMap.getParentKey().equals(fieldMap.getParentEntityMapper().getInternalIdentifierFieldName())) {
                if (deferredEntityDataMappings != null) {
                    deferredEntityDataMappings.add(new OneToManyJoinedEntityComponentDataMapping(fieldMap, dataMap));
                }
                return;
            }

            // Handle association components (many to one)
            if (fieldMap.getEntityMapper().getExternalIdentifierFieldName() != null) {

                // If the association component uses entity map cascade-update (non-hibernate), save the component first so that we can add it to the target
                // (e.g. member -> contact, account-type -> image)
                if (fieldMap.isCascadeUpdate()) {
                    if (preemptedEntityDataMappings != null && dataMap != null) {
                        if (fieldMap.isJoin()) {
                            preemptedEntityDataMappings.add(new ManyToOneJoinedEntityComponentDataMapping(fieldMap, dataMap, target, modified));
                        }
                    }
                    return;
                }
            }

            if (fieldMap.isForSubclass()) {
                if (fieldMap.getEntityMapper().getBaseClass().isInstance(target)) {
                    fieldMap.getEntityMapper().toEntity(dataMap, target, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter, modified);
                }
                return;
            }

            currentValue = PropertyUtils.getProperty(target, fieldMap.getInternalFieldName());
        }

        // Populate component (new or existing)
        if (currentValue == null || (!fieldMap.isCascadeDelete() && !fieldMap.isCascadeUpdate() && StringUtils.isNotBlank(fieldMap.getEntityMapper().getInternalIdentifierFieldName()))) {

            // in this case only compare the id to check if it has been modified
            if (modified != null) {
                Object identifierA = currentValue != null ? PropertyUtils.getProperty(currentValue, fieldMap.getEntityMapper().getInternalIdentifierFieldName()) : null;
                Object identifierB = dataMap != null ? dataMap.get(fieldMap.getEntityMapper().getInternalIdentifierFieldName()) : null;

                if (!Objects.equals(identifierA, identifierB)) {
                    modified.set(true);
                }
            }

            // in case the current value is null it needs to set modified to true as it is creating a new instance and assigning to the entity data
            if (currentValue == null && modified != null) {
                modified.set(true);
            }

            Object newValue = fieldMap.getEntityMapper().toEntity(dataMap, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter);
            if (fieldMap.isAttribute()) {
                ((Attributed) target).setAttribute(fieldMap.getInternalFieldName(), newValue);
            } else {
                PropertyUtils.setProperty(target, fieldMap.getInternalFieldName(), newValue);
            }
        } else {
            fieldMap.getEntityMapper().toEntity(dataMap, currentValue, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter, modified);
        }
    }

    @Override
    public void handleObjectToMap(ComponentMap fieldMap, Object source, DataMap targetMap, SessionContext sessionContext) throws Exception {
        if (!fieldMap.operationAllowed(EntityMapper.Operation.READ)) {
            return;
        }

        DataMap componentMapData = null;
        if (fieldMap.isForSubclass()) {
            if (fieldMap.getEntityMapper().getBaseClass().isInstance(source)) {
                componentMapData = fieldMap.getEntityMapper().toMap(source, sessionContext);
            }
        } else if ((fieldMap.isAttribute() && source instanceof Attributed) || (!fieldMap.isAttribute() && PropertyUtils.isReadable(source, fieldMap.getInternalFieldName()))) {
            Object propertyValue;
            if (fieldMap.isAttribute()) {
                propertyValue = ((Attributed) source).getAttribute(fieldMap.getInternalFieldName());
            } else {
                propertyValue = PropertyUtils.getProperty(source, fieldMap.getInternalFieldName());
            }
            if (propertyValue == null) {
                if (!fieldMap.isFlattened()) {
                    targetMap.put(fieldMap.getExternalFieldName(), null);
                }
                return;
            }
            componentMapData = fieldMap.getEntityMapper().toMap(propertyValue, sessionContext);
        }

        if (componentMapData != null) {
            if (fieldMap.isFlattened()) {
                componentMapData.forEach((s, o) -> targetMap.put(s, o));
            } else {
                targetMap.put(fieldMap.getExternalFieldName(), componentMapData);
            }
        }
    }

    @Override
    public void applyDefaultValue(ComponentMap fieldMap, Object target, EntityMapper.Operation operation, FieldConverter fieldConverter) {
    }

    @Override
    public Class<ComponentMap> canHandleClass() {
        return ComponentMap.class;
    }
}