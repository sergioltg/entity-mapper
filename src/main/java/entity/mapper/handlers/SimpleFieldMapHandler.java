package entity.mapper.handlers;

import entity.mapper.Attributed;
import entity.mapper.EntityDataMapping;
import entity.mapper.EntityMapper;
import entity.mapper.datamap.DataMap;
import entity.mapper.field.convert.FieldConverter;
import entity.mapper.fieldmaps.FieldMap;
import entity.mapper.fieldmaps.SimpleFieldMap;
import entity.session.SessionContext;
import org.apache.commons.beanutils.PropertyUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the SimpleFieldMap conversion
 */
public class SimpleFieldMapHandler implements MapHandler<SimpleFieldMap> {

    @Override
    public void handleMapToObject(SimpleFieldMap fieldMap, Object value, Object target, DataMap parentDataMap, EntityMapper.Operation operation,
                                  Collection<EntityDataMapping> preemptedEntityDataMappings,
                                  Collection<EntityDataMapping> deferredEntityDataMappings, FieldConverter fieldConverter, AtomicBoolean modified) throws Exception {
        if (!fieldMap.operationAllowed(operation)) {
            return;
        }

        // Skip update of a sensitive field if the value is the password mask (value has not been changed)
        if (fieldMap.getAccess() == FieldMap.Access.SENSITIVE && FieldMap.PASSWORD_MASK.equals(value)) {
            return;
        }

        if (fieldMap.isAttribute()) {
            if (!(target instanceof Attributed)) {
                return;
            }
            if (value == null) {
                ((Attributed) target).removeAttribute(fieldMap.getInternalFieldName());
            } else {
                ((Attributed) target).setAttribute(fieldMap.getInternalFieldName(), value);
            }
        } else {
            if (modified != null && !fieldMap.getInternalFieldName().equals("version")) {
                if (!objectEquals(PropertyUtils.getProperty(target, fieldMap.getInternalFieldName()), value)) {
                    modified.set(true);
                }
            }
            PropertyUtils.setProperty(target, fieldMap.getInternalFieldName(), value);
        }
    }

    private boolean objectEquals(Object a, Object b) {
        if (a instanceof BigDecimal || b instanceof BigDecimal) {
            return (a == b) || (a != null && b != null && ((BigDecimal)a).compareTo((BigDecimal) b) == 0);
        } else {
            return Objects.equals(a, b);
        }
    }

    @Override
    public void handleObjectToMap(SimpleFieldMap fieldMap, Object source, DataMap targetMap, SessionContext sessionContext) throws Exception {
        if (!fieldMap.operationAllowed(EntityMapper.Operation.READ)) {
            return;
        }
        Object propertyValue;
        if (fieldMap.isAttribute()) {
            if (!(source instanceof Attributed)) {
                return;
            }
            propertyValue = ((Attributed) source).getAttribute(fieldMap.getInternalFieldName());
        } else {
            propertyValue = PropertyUtils.getProperty(source, fieldMap.getInternalFieldName());
        }
        if (fieldMap.getAccess() == FieldMap.Access.SENSITIVE && propertyValue != null && !"".equals(propertyValue)) {
            propertyValue = FieldMap.PASSWORD_MASK;
        }
        if (propertyValue != null) {
            targetMap.put(fieldMap.getExternalFieldName(), propertyValue);
        }
    }

    @Override
    public void applyDefaultValue(SimpleFieldMap fieldMap, Object target, EntityMapper.Operation operation, FieldConverter fieldConverter) throws Exception {
        if (fieldMap.getDefaultValue() == null || !fieldMap.operationAllowed(operation)) {
            return;
        }

        Object defaultValue = fieldConverter == null ? fieldMap.getDefaultValue() : fieldConverter.toInternal(fieldMap.getExternalFieldName(), fieldMap.getDefaultValue().getClass(), fieldMap.getDefaultValue());

        if (fieldMap.isAttribute()) {
            if (((Attributed) target).getAttribute(fieldMap.getInternalFieldName()) == null) {
                ((Attributed) target).setAttribute(fieldMap.getInternalFieldName(), defaultValue);
            }
        } else {
            if (PropertyUtils.getProperty(target, fieldMap.getInternalFieldName()) == null) {
                PropertyUtils.setProperty(target, fieldMap.getInternalFieldName(), defaultValue);
            }
        }
    }

    @Override
    public Class<SimpleFieldMap> canHandleClass() {
        return SimpleFieldMap.class;
    }
}