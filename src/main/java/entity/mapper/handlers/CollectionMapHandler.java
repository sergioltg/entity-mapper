package entity.mapper.handlers;

import entity.mapper.Attributed;
import entity.mapper.EntityCollectionDataMapping;
import entity.mapper.EntityDataMapping;
import entity.mapper.EntityMapper;
import entity.mapper.datamap.DataMap;
import entity.mapper.field.convert.FieldConverter;
import entity.mapper.fieldmaps.CollectionMap;
import entity.session.SessionContext;
import org.apache.commons.beanutils.PropertyUtils;

import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Handles a CollectionMap conversion.
 */
public class CollectionMapHandler implements MapHandler<CollectionMap> {

    @Override
    public void handleMapToObject(CollectionMap fieldMap, Object value, Object target, DataMap parentDataMap, EntityMapper.Operation operation,
                                  Collection<EntityDataMapping> preemptedEntityDataMappings,
                                  Collection<EntityDataMapping> deferredEntityDataMappings, FieldConverter fieldConverter, AtomicBoolean modified) throws Exception {
        if (value == null) {
            return;
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Value should be a List");
        }
        if (!fieldMap.operationAllowed(operation)) {
            return;
        }
        if (fieldMap.isAttribute() && !(target instanceof Attributed)) {
            return;
        }

        // ignore non-updatable joins
        if (fieldMap.isJoin() && !fieldMap.isCascadeUpdate()) {
            return;
        }

        if (fieldMap.isJoin() && !fieldMap.isAttribute()) {
            if (deferredEntityDataMappings != null) {
                deferredEntityDataMappings.add(new EntityCollectionDataMapping(fieldMap, (List) value));
            }
            return;
        }

        PropertyDescriptor propertyDescriptor = PropertyUtils.getPropertyDescriptor(target, fieldMap.getInternalFieldName());
        if (!Collection.class.isAssignableFrom(propertyDescriptor.getPropertyType())) {
            throw new Exception("Target " + fieldMap.getInternalFieldName() + " is not a Collection type");
        }

        // For one-to-many collections with orphan deletion, we must update the existing collection (if any) so that hibernate can track removals properly.
        // For other collections, we must replace the existing collection entirely, or else hibernate tries to insert duplicate rows on an update.
        Collection existingCollection = fieldMap.isAttribute()
                ? (Collection) ((Attributed) target).getAttribute(fieldMap.getInternalFieldName())
                : (Collection) PropertyUtils.getProperty(target, fieldMap.getInternalFieldName());
        Collection collection;
        if (fieldMap.isOneToMany() && fieldMap.isDeleteOrphan() && existingCollection != null) {
            collection = existingCollection;
            existingCollection = (Collection) existingCollection.stream().collect(Collectors.toList());
            collection.clear();
        } else {
            collection = instantiateCollectionForProperty(propertyDescriptor.getPropertyType());
            if (fieldMap.isAttribute()) {
                ((Attributed) target).setAttribute(fieldMap.getInternalFieldName(), collection);
            } else {
                PropertyUtils.setProperty(target, fieldMap.getInternalFieldName(), collection);
            }
        }

        // in some cases the hibernate field idx is getting wrong (skipping one position) causing the collection to be null
        // so removing the null from the existing collection will fix the collection
        if (existingCollection != null) {
            if (existingCollection.removeIf(Objects::isNull)) {
                modified.set(true);
            }
        }

        List newCollection = (List) value;

        // check if the collection has been modified
        if (modified != null) {
            if (fieldMap.getPrimitiveType() != null) {
                if (!Objects.equals(newCollection != null ? new ArrayList<>(newCollection) : null, existingCollection != null ? new ArrayList<>(existingCollection) : null)) {
                    modified.set(true);
                }
            } else if (fieldMap.getEntityMapper().getInternalIdentifierFieldName() == null) {
                // cannot compare the list without a identifier so assumes the list changed
                modified.set(true);
            } else if (newCollection == null || existingCollection == null) {
                if (!(newCollection == null && existingCollection == null)) {
                    modified.set(true);
                }
            } else if (newCollection.size() != existingCollection.size()) {
                modified.set(true);
            } else {
                List existingList = new ArrayList(existingCollection);
                for (int index = 0; index < existingCollection.size(); index++) {
                    Object newItem = newCollection.get(index);
                    Object existingItem = existingList.get(index);
                    Object newKey = PropertyUtils.getProperty(newItem, fieldMap.getEntityMapper().getInternalIdentifierFieldName());
                    Object existingKey = PropertyUtils.getProperty(existingItem, fieldMap.getEntityMapper().getInternalIdentifierFieldName());
                    if (!Objects.equals(newKey, existingKey)) {
                        modified.set(true);
                        break;
                    }
                }
            }
        }

        collection:
        for (Object item : newCollection) {
            if (fieldMap.getPrimitiveType() != null) {
                collection.add(item);
            } else {
                DataMap itemMap = (DataMap) item;
                if (itemMap != null) {
                    itemMap.setParent(parentDataMap);
                }

                // To allow for partial mapping or lazy loading of newCollection in a collection, update and reuse an existing item based on a key search.
                String identifierFieldName = fieldMap.getEntityMapper().getExternalIdentifierFieldName();
                if (identifierFieldName != null) {
                    Object key = itemMap.get(identifierFieldName);
                    if (key != null && existingCollection != null) {
                        for (Object existingItem : existingCollection) {
                            Object existingKey = PropertyUtils.getProperty(existingItem, fieldMap.getEntityMapper().getInternalIdentifierFieldName());
                            if (existingKey.equals(key)) {
                                fieldMap.getEntityMapper().toEntity(itemMap, existingItem, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter, modified);
                                collection.add(existingItem);
                                continue collection;
                            }
                        }
                    }
                }

                Object object = fieldMap.getEntityMapper().toEntity(itemMap, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter);
                collection.add(object);
            }
        }
    }

    @Override
    public void handleObjectToMap(CollectionMap fieldMap, Object source, DataMap targetMap, SessionContext sessionContext) throws Exception {
        if (!fieldMap.operationAllowed(EntityMapper.Operation.READ) || !PropertyUtils.isReadable(source, fieldMap.getInternalFieldName())) {
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
        if (propertyValue == null) {
            targetMap.put(fieldMap.getExternalFieldName(), null);
            return;
        }
        if (!(propertyValue instanceof Collection)) {
            throw new IllegalArgumentException("Source " + fieldMap.getInternalFieldName() + " is not a collection type");
        }
        Collection<Object> collection = (Collection) propertyValue;
        List<Object> list = new ArrayList<>();
        for (Object object : collection) {
            if (object == null) {
                continue;
            }
            if (fieldMap.getPrimitiveType() != null) {
                list.add(object);
            } else {
                list.add(fieldMap.getEntityMapper().toMap(object, sessionContext));
            }
        }

        targetMap.put(fieldMap.getExternalFieldName(), list);
    }

    @Override
    public void applyDefaultValue(CollectionMap fieldMap, Object target, EntityMapper.Operation operation, FieldConverter fieldConverter) {
    }

    private Collection instantiateCollectionForProperty(Class collectionType) throws Exception {
        if (List.class.isAssignableFrom(collectionType)) {
            return new ArrayList();
        } else if (SortedSet.class.isAssignableFrom(collectionType)) {
            return new TreeSet();
        } else if (Set.class.isAssignableFrom(collectionType)) {
            return new HashSet();
        } else {
            throw new Exception("Collection type unknown for target");
        }
    }

    @Override
    public Class<CollectionMap> canHandleClass() {
        return CollectionMap.class;
    }
}