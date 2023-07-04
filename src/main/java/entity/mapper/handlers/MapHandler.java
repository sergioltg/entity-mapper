package entity.mapper.handlers;

import entity.mapper.EntityDataMapping;
import entity.mapper.EntityMapper;
import entity.mapper.datamap.DataMap;
import entity.mapper.field.convert.FieldConverter;
import entity.mapper.fieldmaps.FieldMap;
import entity.session.SessionContext;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Defines an interface for handling field map conversion.
 */
public interface MapHandler<E extends FieldMap> {
    /**
     * Handle mapping a map to an object
     *
     * @param fieldMap                    Field map used to do the conversion
     * @param value                       Value in the object
     * @param target                      Target object to get the value
     * @param parentDataMap
     * @param operation                   CREATE or UPDATE - controls how FINAL or READONLY fields are mapped.
     * @param preemptedEntityDataMappings Any preempted entity mappings are added to this collection.
     * @param deferredEntityDataMappings  Any deferred entity mappings are added to this collection.
     * @param fieldConverter
     * @param modified
     * @throws Exception
     */
    void handleMapToObject(E fieldMap, Object value, Object target, DataMap parentDataMap, EntityMapper.Operation operation,
                           Collection<EntityDataMapping> preemptedEntityDataMappings, Collection<EntityDataMapping> deferredEntityDataMappings,
                           FieldConverter fieldConverter, AtomicBoolean modified) throws Exception;

    /**
     * Handle mapping an object to a map
     *
     * @param fieldMap             Field map used to do the conversion
     * @param source               Source object to provide the values
     * @param targetMap            Target map
     * @param sessionContext       Session Context which may be used by external map handlers
     * @throws Exception
     */
    void handleObjectToMap(E fieldMap, Object source, DataMap targetMap, SessionContext sessionContext) throws Exception;

    /**
     * Handle applying the default value for a map to an object
     *
     * @param fieldMap                    Field map used to do the conversion
     * @param target                      Target object to get the value
     * @param operation                   CREATE or UPDATE - controls how FINAL or READONLY fields are mapped.
     * @param fieldConverter
     * @throws Exception
     */
    void applyDefaultValue(E fieldMap, Object target, EntityMapper.Operation operation, FieldConverter fieldConverter) throws Exception;

    /**
     * Returns the FieldMap class that the handler can handle
     *
     * @return
     */
    Class<E> canHandleClass();

}
