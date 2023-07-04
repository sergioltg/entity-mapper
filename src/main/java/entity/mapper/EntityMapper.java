package entity.mapper;

import entity.mapper.datamap.DataMap;
import entity.mapper.field.convert.FieldConverter;
import entity.mapper.fieldmaps.*;
import entity.mapper.fieldtyper.FieldTyper;
import entity.mapper.handlers.CollectionMapHandler;
import entity.mapper.handlers.ComponentMapHandler;
import entity.mapper.handlers.MapHandler;
import entity.mapper.handlers.SimpleFieldMapHandler;
import entity.mapper.metadata.Reflector;
import entity.mapper.metadata.ReflectorFactory;
import entity.session.SessionContext;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;

import java.beans.FeatureDescriptor;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Maps a value object to an external format using field maps as definition.
 */
public class EntityMapper<E> implements FieldTyper {
    public static final String VERSION_KEY = "version";

    // Define list of handlers for each FieldMap that will map data.
    private static final List<MapHandler<?>> HANDLERS = Arrays.asList(
            new SimpleFieldMapHandler(),
            new CollectionMapHandler(),
            new ComponentMapHandler()
    );
    // Map to the handlers by FieldMap
    private static final Map<Class<? extends FieldMap>, MapHandler<?>> mapHandlers = new LinkedHashMap<>();

    static {
        for (MapHandler<?> handler : HANDLERS) {
            mapHandlers.put(handler.canHandleClass(), handler);
        }
    }

    // Class that will be mapped
    private EntityClassSpec<E> baseClassSpec;
    private Map<String, EntityClassSpec<E>> subClassSpecs;
    // Handler to be used for external processing of the entity
    private SimpleFieldMap identifierFieldMap;
    private SimpleFieldMap entityNameFieldMap;
    // Collection containing all Field maps
    private Collection<FieldMap> fieldMaps;
    // Map containing the fieldMaps indexing by Type
    private Map<Class<? extends FieldMap>, List<FieldMap>> mapByType;
    // Map containing the fieldMaps indexing by internal field name
    private Map<String, FieldMap> mapByInternalFieldName;
    // Map containing the fieldMaps indexing by external field name
    private Map<String, FieldMap> mapByExternalFieldName;
    // Map of fields and its types. The map key is the field path. For example: employeeContact.employeeName
    private Map<String, Class> mapFieldTypes;
    // Map containing an index to transform a external field path to a internal field path
    private Map<String, String> mapExternalToInternalFieldPath;
    // Discriminator field map (if subtypes are specified)
    private DiscriminatorFieldMap discriminatorFieldMap;
    private boolean discriminatorSpecified;

    private FieldMap parentFieldMap;

    private EntityMapper(EntityClassSpec<E> baseClassSpec, Map<String, EntityClassSpec<E>> subClassSpecs, String externalDiscriminatorName, Collection<FieldMap> fieldMaps) {
        this.baseClassSpec = baseClassSpec;
        this.subClassSpecs = subClassSpecs;
        this.fieldMaps = new ArrayList<>(fieldMaps);

        Reflector reflector = ReflectorFactory.getInstance().getReflector();

        if (reflector.isVersioned(getBaseClass())) {
            this.fieldMaps.addAll(SimpleFieldMap.fieldMap(VERSION_KEY, VERSION_KEY).getFieldMaps());
        }

        if (!subClassSpecs.isEmpty()) {
            discriminatorSpecified = externalDiscriminatorName != null;
            String hibernateDiscriminatorName = getBaseClass() == Object.class ? null : reflector.getDiscriminatorName(getBaseClass());
            discriminatorFieldMap = new DiscriminatorFieldMap(
                    hibernateDiscriminatorName,
                    discriminatorSpecified ? externalDiscriminatorName : hibernateDiscriminatorName,
                    hibernateDiscriminatorName != null && reflector.getFieldMetaData(getBaseClass(), hibernateDiscriminatorName) != null);
            this.fieldMaps.add(discriminatorFieldMap);
        }

        checkSubClassMaps();

        checkFlattenedComponentMaps();

        buildMapIndexes();

        checkUniqueIdentifier();
    }

    /**
     * Returns a builder for the entity
     *
     * @return Builder that will create the Mapper instance
     */
    public static <T> MapperBuilder entity(Class<T> objectClass) {
        return new MapperBuilder<>(new EntityClassSpec<>(objectClass));
    }

    /**
     * Returns a parsed EntityMapper.
     * This is a convenience method which simply creates the EntityMapParser and calls the parse() method.
     *
     * @param input       the ERM specification
     * @param ermFileName the file from which the ERM specification was loaded (may be null)
     * @return The parsed EntityResourceMap
     */
    public static <T> EntityMapper<T> parse(String input, String ermFileName) {
        return new EntityMapParser<T>(input, ermFileName).parse();
    }

    public static <T> EntityMapper<T> parse(String input) {
        return new EntityMapParser<T>(input, null).parse();
    }

    public static <T> MapperBuilder entity(EntityClassSpec<T> entityClassSpec) {
        return new MapperBuilder<>(entityClassSpec);
    }

    public void merge(EntityMapper other) {
        for (FieldMap fieldMap : (Collection<FieldMap>) other.fieldMaps) {
            List<FieldMap> fieldMapList = mapByType.computeIfAbsent(fieldMap.getClass(), k -> new ArrayList<>());
            fieldMapList.add(fieldMap);
        }
        fieldMaps.addAll(other.fieldMaps);
        mapFieldTypes.putAll(other.mapFieldTypes);
        mapExternalToInternalFieldPath.putAll(other.mapExternalToInternalFieldPath);
        mapByInternalFieldName.putAll(other.mapByInternalFieldName);
        mapByExternalFieldName.putAll(other.mapByExternalFieldName);
    }

    private void checkSubClassMaps() {
        for (FieldMap fieldMap : fieldMaps) {
            if (fieldMap instanceof ComponentMap) {
                ComponentMap componentMap = (ComponentMap) fieldMap;
                if (componentMap.isForSubclass() && subClassSpecs.values().stream().noneMatch(entityClassSpec -> entityClassSpec.entityClass == componentMap.getEntityMapper().getBaseClass())) {
                    throw new EntityMapperException(String.format("Subclass mapping for '%s' specifies subclass '%s' which is not declared as a subclass for %s.",
                            fieldMap.getExternalFieldName(), ((ComponentMap) fieldMap).getEntityMapper().getBaseClass().getName(), getBaseClass().getName()));
                }
            }
        }
    }

    /**
     * Check if any fields from the flattened maps are going to clash with the parent fields
     */
    private void checkFlattenedComponentMaps() {
        Set<String> fields = getFieldMaps().stream().map(fieldMap -> fieldMap.getExternalFieldName()).collect(Collectors.toCollection(HashSet::new));
        checkFlattenedFields(fields, this);
    }

    private void checkFlattenedFields(Set<String> fields, EntityMapper entityMapper) {
        for (FieldMap fieldMap : (Collection<FieldMap>) entityMapper.getFieldMaps()) {
            if (fieldMap instanceof ComponentMap) {
                ComponentMap componentMap = (ComponentMap) fieldMap;
                if (componentMap.isFlattened()) {
                    for (FieldMap componentFieldMap : (Collection<FieldMap>) componentMap.getEntityMapper().getFieldMaps()) {
                        if (fields.contains(componentFieldMap.getExternalFieldName())) {
                            throw new EntityMapperException(String.format("Flattened component mapping %s is clashing with existing fields in the parent mapping for field %s",
                                    componentMap.getEntityMapper().getBaseClass().getName(), componentFieldMap.getExternalFieldName()));
                        }
                        fields.add(componentFieldMap.getExternalFieldName());
                    }
                    checkFlattenedFields(fields, componentMap.getEntityMapper());
                }
            }
        }
    }

    private void checkUniqueIdentifier() {
        if (findByType(SimpleFieldMap.class).stream().filter(SimpleFieldMap::isIdentifier).count() > 1) {
            throw new EntityMapperException("More than one identifier was found in the mapping.");
        }
    }

    public FieldMap getParentFieldMap() {
        return parentFieldMap;
    }

    public void setParentFieldMap(FieldMap parentFieldMap) {
        this.parentFieldMap = parentFieldMap;
    }

    private void buildMapIndexes() {
        mapByType = new LinkedHashMap<>();
        mapByInternalFieldName = new LinkedHashMap<>();
        mapByExternalFieldName = new LinkedHashMap<>();
        mapFieldTypes = new HashMap<>();
        mapExternalToInternalFieldPath = new HashMap<>();

        // create the fields for the field Maps
        for (FieldMap fieldMap : fieldMaps) {
            fieldMap.setParentEntityMapper(this);

            // determine the identifier field map
            if (fieldMap instanceof SimpleFieldMap) {
                SimpleFieldMap simpleFieldMap = (SimpleFieldMap) fieldMap;
                if (simpleFieldMap.isIdentifier()) {
                    identifierFieldMap = simpleFieldMap;
                } else if (simpleFieldMap.isEntityName()) {
                    entityNameFieldMap = simpleFieldMap;
                }
            }
            // by type
            List<FieldMap> fieldMapList = mapByType.computeIfAbsent(fieldMap.getClass(), k -> new ArrayList<>());
            fieldMapList.add(fieldMap);
        }

        buildFieldTypesMap(this);
        buildExternalToInternalPathMap(this, new String[0], new String[0]);
    }

    /**
     * Builds a map of field types for all fields within this mapper and its descendant mappers
     *
     * @param mapper Mapper to generate the field types
     * @param path   Array containing the field path
     */
    private void buildFieldTypesMap(EntityMapper<?> mapper, String... path) {
        // build a map with the property descriptors
        if (mapper.getBaseClass() == null) {
            return;
        }
        PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(mapper.getBaseClass());
        Map<String, PropertyDescriptor> propertyDescriptorMap = Arrays.stream(propertyDescriptors).collect(Collectors.toMap(FeatureDescriptor::getName, p -> p));

        Collection<FieldMap> fieldMaps = mapper.getFieldMaps();
        for (FieldMap fieldMap : fieldMaps) {

            // Skip the merged field maps in a merged collection map
            if (mapper.getParentFieldMap() instanceof MergedCollectionMap && fieldMap.getParentEntityMapper() != mapper) {
                continue;
            }

            Stream<String> fieldPathStream = Stream.concat(Stream.of(path), Stream.of(fieldMap.getExternalFieldName()));

            if (fieldMap instanceof DiscriminatorFieldMap) {
                mapFieldTypes.put(fieldPathStream.collect(joining(".")), String.class);
            } else if (fieldMap instanceof SimpleFieldMap) {
                SimpleFieldMap simpleFieldMap = (SimpleFieldMap) fieldMap;
                PropertyDescriptor propertyDescriptor = propertyDescriptorMap.get(fieldMap.getInternalFieldName());
                if (!FieldMap.Access.READONLY.equals(simpleFieldMap.getAccess())) {
                    if (propertyDescriptor == null) {
                        throw new RuntimeException("Field '" + fieldMap.getInternalFieldName() + "' of " + mapper.getBaseClass().getName() + " not found.");
                    }
                    mapFieldTypes.put(fieldPathStream.collect(joining(".")), propertyDescriptor.getPropertyType());
                }
            } else if (fieldMap instanceof ComponentMap) {
                ComponentMap componentMap = (ComponentMap) fieldMap;
                if (componentMap.isFlattened()) {
                    buildFieldTypesMap(componentMap.getEntityMapper(), Stream.of(path).toArray(String[]::new));
                } else {
                    buildFieldTypesMap(componentMap.getEntityMapper(), fieldPathStream.toArray(String[]::new));
                }

            } else if (fieldMap instanceof CollectionMap) {
                CollectionMap c = (CollectionMap) fieldMap;

                if (mapper.getParentFieldMap() instanceof MergedCollectionMap) {
                    // Direct access to contents of a merged collection's child collections (skip the child collections in the path)
                    buildFieldTypesMap(c.getEntityMapper(), path);
                } else if (c.getPrimitiveType() != null) {
                    mapFieldTypes.put(fieldPathStream.collect(joining(".")), c.getPrimitiveType());
                } else {
                    buildFieldTypesMap(c.getEntityMapper(), fieldPathStream.toArray(String[]::new));
                }
            }
        }
    }

    /**
     * Build the map containing the index to convert an externalFieldPath to an internalFieldPath
     *
     * @param mapper
     * @param externalFieldPath
     * @param internalFieldPath
     */
    private void buildExternalToInternalPathMap(EntityMapper<?> mapper, String[] externalFieldPath, String[] internalFieldPath) {
        Collection<FieldMap> fieldMaps = mapper.getFieldMaps();
        for (FieldMap fieldMap : fieldMaps) {
            Stream<String> externalFieldPathStream = Stream.concat(Stream.of(externalFieldPath), Stream.of(fieldMap.getExternalFieldName()));
            Stream<String> internalFieldPathStream = Stream.concat(Stream.of(internalFieldPath), Stream.of(fieldMap.getInternalFieldName()));
            String externalPath = externalFieldPathStream.collect(joining("."));
            String internalPath = internalFieldPathStream.collect(joining("."));

            mapExternalToInternalFieldPath.put(externalPath, internalPath);

            // by internal field name
            mapByInternalFieldName.put(internalPath, fieldMap);
            // by external field name
            mapByExternalFieldName.put(externalPath, fieldMap);

            if (fieldMap instanceof EntityFieldMap) {
                if (((EntityFieldMap) fieldMap).getEntityMapper() != null) {
                    buildExternalToInternalPathMap(((EntityFieldMap) fieldMap).getEntityMapper(), externalPath.split("\\."), internalPath.split("\\."));
                }
            }
        }
    }

    /**
     * Transform the map to a new instance of the defined class
     *
     * @param map                         map containing a compatible structure with the mapping definition
     * @param operation                   CREATE or UPDATE - controls how FINAL or READONLY fields are mapped.
     * @param preemptedEntityDataMappings Any preempted entity mappings are added to this collection.
     * @param deferredEntityDataMappings  Any deferred entity mappings are added to this collection.
     * @param fieldConverter
     * @return new instance of the defined class
     */
    public E toEntity(DataMap map, Operation operation, Collection<EntityDataMapping> preemptedEntityDataMappings, Collection<EntityDataMapping> deferredEntityDataMappings, FieldConverter fieldConverter) {
        Class<E> entityClass = null;
        if (discriminatorFieldMap != null) {
            String externalDiscriminatorValue = (String) map.get(discriminatorFieldMap.getExternalFieldName());
            if (externalDiscriminatorValue != null) {
                entityClass = getDiscriminatedSubClass(externalDiscriminatorValue);
            }
        }
        if (entityClass == null) {
            entityClass = getBaseClass();
        }

        E newInstance;
        try {
            newInstance = entityClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new EntityMapperException("Error creating a new instance of the object class " + entityClass.getName(), e);
        }
        toEntity(map, newInstance, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter, null);
        return newInstance;
    }

    /**
     * Merge the map to the object target
     *
     * @param map                         map containing a compatible structure with the mapping definition
     * @param target                      target object that will get the data merged
     * @param operation                   CREATE or UPDATE - controls how FINAL or READONLY fields are mapped.
     * @param preemptedEntityDataMappings Any preempted entity mappings are added to this collection.
     * @param deferredEntityDataMappings  Any deferred entity mappings are added to this collection.
     * @param fieldConverter
     */
    public void toEntity(DataMap map, E target, Operation operation, Collection<EntityDataMapping> preemptedEntityDataMappings,
                         Collection<EntityDataMapping> deferredEntityDataMappings, FieldConverter fieldConverter, AtomicBoolean modified) {
        for (FieldMap fieldMap : fieldMaps) {
            try {
                getHandler(fieldMap).applyDefaultValue(fieldMap, target, operation, fieldConverter);
            } catch (Exception e) {
                throw new EntityMapperException("Error applying default values for the map", e);
            }
        }

        // handle flattened component maps
        for (ComponentMap componentMap : fieldMaps.stream()
                .filter(ComponentMap.class::isInstance)
                .map(ComponentMap.class::cast)
                .filter(fieldMap -> fieldMap.isFlattened())
                .collect(Collectors.toList())) {
            DataMap componentMapData = new DataMap();
            for (FieldMap componentFieldMap : (Collection<FieldMap>) componentMap.getEntityMapper().getFieldMaps()) {
                if (map.containsKey(componentFieldMap.getExternalFieldName())) {
                    componentMapData.put(componentFieldMap.getExternalFieldName(), map.get(componentFieldMap.getExternalFieldName()));
                    map.remove(componentFieldMap.getExternalFieldName());
                }
            }
            if (!componentMapData.isEmpty()) {
                map.put(componentMap.getExternalFieldName(), componentMapData);
            }
        }

        for (String externalFieldName : map.keySet()) {
            FieldMap fieldMap = findByExternalFieldName(externalFieldName);
            if (fieldMap != null) {
                try {
                    getHandler(fieldMap).handleMapToObject(fieldMap, map.get(externalFieldName), target, map, operation, preemptedEntityDataMappings, deferredEntityDataMappings, fieldConverter, modified);
                } catch (Exception e) {
                    throw new EntityMapperException("Error converting the map to an object", e);
                }
            }
        }
    }

    /**
     * Transform an entity to a map
     *
     * @param entity  entity to be transformed to a map
     * @param context Session Context which may be used by external map handlers
     * @return map containing the values
     */
    public DataMap toMap(E entity, SessionContext context) {
        return toMap(entity, null, context);
    }

    /**
     * Transform an entity or subset thereof to a map
     *
     * @param entityData      entity or sub-collection/component to be transformed to a map
     * @param subResourcePath Identifies the nested collection or component within the entity map
     * @param context         Session Context which may be used by external map handlers
     * @return map containing the values
     */
    public DataMap toMap(Object entityData, String subResourcePath, SessionContext context) {
        if (StringUtils.isNotBlank(subResourcePath)) {
            return getEntityMapperForSubResourcePath(subResourcePath).toMap(entityData, null, context);
        }

        DataMap map = new DataMap();
        for (FieldMap fieldMap : getFieldMaps()) {
            try {
                getHandler(fieldMap).handleObjectToMap(fieldMap, entityData, map, context);
            } catch (Exception e) {
                throw new EntityMapperException("Error converting a object to the map", e);
            }
        }

        return map;
    }

    /**
     * @param subResourcePath A period-separated path to the sub-resource
     * @return an EntityMapper for a subResourcePath
     */
    public EntityMapper getEntityMapperForSubResourcePath(String subResourcePath) {
        EntityMapper entityMapper = this;

        for (String fieldName : subResourcePath.split("\\.")) {
            FieldMap fieldMap = entityMapper.findByExternalFieldName(fieldName);
            if (fieldMap == null) {
                throw new EntityMapperException("No such field as " + fieldName + " in map for " + getBaseClassAlias());
            }
            if (!(fieldMap instanceof EntityFieldMap)) {
                throw new EntityMapperException("Field " + fieldName + " in map for " + getBaseClassAlias() + " is not an entity field map");
            }
            entityMapper = ((EntityFieldMap) fieldMap).getEntityMapper();
        }

        return entityMapper;
    }

    /**
     * Return the identifier for the map
     *
     * @param map Map containing the values
     * @return Value for the identifier. Null if there is no identifier.
     */
    public Serializable resolveIdentifierForMap(DataMap map) {
        if (identifierFieldMap != null) {
            return (Serializable) map.get(identifierFieldMap.getExternalFieldName());
        }
        return null;
    }

    /**
     * Return the identifier for the entity
     *
     * @param entity Object
     * @return Value for the identifier. Null if there is no identifier.
     */
    public Object resolveIdentifierForEntity(E entity) {
        return resolveIdentifierForEntityData(entity);
    }

    /**
     * Return the identifier for an entity or subset thereof
     *
     * @param entityData      entity or sub-collection/component
     * @param subResourcePath Identifies the nested collection or component within the entity map
     * @return Value for the identifier. Null if there is no identifier.
     */
    public Object resolveIdentifierForEntityData(Object entityData, String subResourcePath) {
        return getEntityMapperForSubResource(subResourcePath).resolveIdentifierForEntityData(entityData);
    }

    /**
     * Return the identifier for an entity data
     *
     * @param entityData entity or sub-collection/component
     * @return Value for the identifier. Null if there is no identifier.
     */
    public Object resolveIdentifierForEntityData(Object entityData) {
        if (identifierFieldMap != null) {
            try {
                return PropertyUtils.getProperty(entityData, identifierFieldMap.getInternalFieldName());
            } catch (Exception e) {
                throw new EntityMapperException("Error getting identifier for object", e);
            }
        }
        return null;
    }

    /**
     * Returns an nested entity mapper for the specified sub-resource within this entity mapper
     *
     * @param subResourcePath The path of the field map containing the required entity mapper
     * @return The entity mapper
     * @throws EntityMapperException if the specified field does not exist or does not contain a component or collection
     */
    public EntityMapper getEntityMapperForSubResource(String subResourcePath) {
        if (StringUtils.isBlank(subResourcePath)) {
            return this;
        }
        String fieldName;
        int i = subResourcePath.indexOf('.');
        if (i >= 0) {
            fieldName = subResourcePath.substring(0, i);
            subResourcePath = subResourcePath.substring(i + 1);
        } else {
            fieldName = subResourcePath;
            subResourcePath = null;
        }
        FieldMap fieldMap = findByExternalFieldName(fieldName);
        if (fieldMap == null) {
            throw new EntityMapperException("No such field as " + fieldName + " in map for " + getBaseClassAlias());
        }
        if (!(fieldMap instanceof EntityFieldMap)) {
            throw new EntityMapperException("Field " + fieldName + " in map for " + getBaseClassAlias() + " is not an entity field map");
        }
        return ((EntityFieldMap) fieldMap).getEntityMapper().getEntityMapperForSubResource(subResourcePath);
    }

    /**
     * Returns a field map for the specified sub-resource within this entity mapper
     *
     * @param subResourcePath The path of the field map
     * @return The field map
     */
    public FieldMap getFieldMapForSubResource(String subResourcePath) {
        if (StringUtils.isBlank(subResourcePath)) {
            throw new EntityMapperException("Cannot obtain field map for empty path");
        }
        String fieldName;
        int i = subResourcePath.indexOf('.');
        if (i >= 0) {
            fieldName = subResourcePath.substring(0, i);
            subResourcePath = subResourcePath.substring(i + 1);
        } else {
            fieldName = subResourcePath;
            subResourcePath = null;
        }
        FieldMap fieldMap = findByExternalFieldName(fieldName);
        if (fieldMap == null) {
            throw new EntityMapperException("No such field as " + fieldName + " in map for " + getBaseClassAlias());
        }
        if (StringUtils.isBlank(subResourcePath)) {
            return fieldMap;
        }
        if (!(fieldMap instanceof EntityFieldMap)) {
            throw new EntityMapperException("Field " + fieldName + " in map for " + getBaseClassAlias() + " is not an entity field map");
        }
        return ((EntityFieldMap) fieldMap).getEntityMapper().getFieldMapForSubResource(subResourcePath);
    }

    /**
     * Return the identifier for an entity
     *
     * @param entity entity
     * @return Value for the identifier as a string. Null if there is no identifier.
     */
    public String resolveIdentifierForEntityAsString(E entity) {
        return resolveIdentifierForEntityDataAsString(entity, null);
    }

    /**
     * Return the identifier for an entity or subset thereof
     *
     * @param entityData      entity or sub-collection/component
     * @param subResourcePath Identifies the nested collection or component within the entity map
     * @return Value for the identifier as a string. Null if there is no identifier.
     */
    public String resolveIdentifierForEntityDataAsString(Object entityData, String subResourcePath) {
        Object identifier = resolveIdentifierForEntityData(entityData, subResourcePath);
        return identifier == null ? null : identifier.toString();
    }

    public String getExternalIdentifierFieldName() {
        if (identifierFieldMap == null) {
            return null;
        }
        return identifierFieldMap.getExternalFieldName();
    }

    public Class getExternalIdentifierFieldType() {
        if (identifierFieldMap == null) {
            return null;
        }
        return getFieldType(identifierFieldMap.getExternalFieldName());
    }


    public String getInternalIdentifierFieldName() {
        if (identifierFieldMap == null) {
            return null;
        }
        return identifierFieldMap.getInternalFieldName();
    }

    public Class getInternalIdentifierFieldType() {
        if (identifierFieldMap == null) {
            return null;
        }
        return getFieldType(identifierFieldMap.getInternalFieldName());
    }

    public String getExternalEntityNameFieldName() {
        String externalEntityNameFieldName = entityNameFieldMap != null ? entityNameFieldMap.getExternalFieldName() : null;

        // look in children
        if (externalEntityNameFieldName == null) {
            for (FieldMap fieldMap : getFieldMaps()) {
                if (fieldMap instanceof ComponentMap) {
                    ComponentMap componentMap = (ComponentMap) fieldMap;
                    externalEntityNameFieldName = componentMap.getEntityMapper().getExternalEntityNameFieldName();
                    if (externalEntityNameFieldName != null) {
                        externalEntityNameFieldName = fieldMap.getExternalFieldName() + "." + externalEntityNameFieldName;
                        break;
                    }
                }
            }
        }

        return externalEntityNameFieldName;
    }

    /**
     * @param fieldMap a field map
     * @return the handler for the field map
     */
    private <F extends FieldMap> MapHandler<F> getHandler(F fieldMap) {
        return (MapHandler<F>) mapHandlers.get(fieldMap.getClass());
    }

    public Class<E> getBaseClass() {
        return baseClassSpec.getEntityClass();
    }

    public String getBaseClassAlias() {
        return baseClassSpec.getAlias();
    }

    public boolean isSuppressLocalisation() {
        return baseClassSpec.isSuppressLocalisation();
    }

    public Class<E> getDiscriminatedSubClass(String externalDiscriminatorValue) {
        return subClassSpecs.get(externalDiscriminatorValue).entityClass;
    }

    public String getSubClassExternalDiscriminatorValue(String className) {
        return subClassSpecs.values().stream().filter(subClassSpec -> subClassSpec.entityClass.getName().equals(className)).map(EntityClassSpec::getExternalDiscriminatorValue).findFirst().orElse(null);
    }

    public Collection<FieldMap> getFieldMaps() {
        return fieldMaps;
    }

    /**
     * Get a FieldMap by internalFieldName
     *
     * @param internalFieldName
     * @return FieldMap or null if not found
     */
    public FieldMap findByInternalFieldName(String internalFieldName) {
        return mapByInternalFieldName.get(internalFieldName);
    }

    /**
     * Get a FieldMap by externalFieldName
     *
     * @param externalFieldName
     * @return FieldMap or null if not found
     */
    public FieldMap findByExternalFieldName(String externalFieldName) {
        return mapByExternalFieldName.get(externalFieldName);
    }

    /**
     * Return a list of field maps filtering by type
     *
     * @param type tpe to be filtered
     * @return List of field maps that implements the type or empty if not type was found
     */
    public <F extends FieldMap> Collection<F> findByType(Class<F> type) {
        Collection<F> collection = (Collection<F>) mapByType.get(type);
        return collection != null ? collection : Collections.emptyList();
    }

    public String findInternalFieldPath(String externalFieldPath) {
        return mapExternalToInternalFieldPath.get(externalFieldPath);
    }

    @Override
    public Class getFieldType(String externalFieldPath) {
        FieldMap fieldMap = mapByExternalFieldName.get(externalFieldPath);
        // don't need type when the field is readonly
        if (fieldMap != null && FieldMap.Access.READONLY.equals(fieldMap.getAccess())) {
            return null;
        }
        // Only get the fieldType when the collection is a primitive
        if (fieldMap instanceof CollectionMap) {
            CollectionMap collectionMap = (CollectionMap) fieldMap;
            if (collectionMap.getPrimitiveType() == null) {
                return null;
            }
        }
        Class fieldType = mapFieldTypes.get(externalFieldPath);
        if (fieldType == null) {
            // do not throw exception when this fieldMap has in its parents any collection map handler
            if (!isFieldChildOfCollectionMapHandler(externalFieldPath) && !isFieldChildOfComponentMapHandler(externalFieldPath)) {
                throw new RuntimeException("Field '" + externalFieldPath + "' not found or was not indexed as it does not relate to a SimpleMap.");
            }
        }
        return fieldType;
    }

    private boolean isFieldChildOfCollectionMapHandler(String externalFieldPath) {
        LinkedList<String> fields = new LinkedList(Arrays.asList(externalFieldPath.split("\\.")));
        while (!fields.isEmpty()) {
            fields.removeLast();
            FieldMap fieldMap = mapByExternalFieldName.get(fields.stream().collect(joining(".")));
        }

        return false;
    }

    private boolean isFieldChildOfComponentMapHandler(String externalFieldPath) {
        LinkedList<String> fields = new LinkedList(Arrays.asList(externalFieldPath.split("\\.")));
        while (!fields.isEmpty()) {
            fields.removeLast();
            FieldMap fieldMap = mapByExternalFieldName.get(fields.stream().collect(joining(".")));
        }

        return false;
    }

    @Override
    public boolean hasFieldType(String externalFieldPath) {
        FieldMap fieldMap = mapByExternalFieldName.get(externalFieldPath);
        // don't need type when the field is readonly
        if (fieldMap != null && FieldMap.Access.READONLY.equals(fieldMap.getAccess())) {
            return false;
        }
        return mapFieldTypes.containsKey(externalFieldPath);
    }

    public DiscriminatorFieldMap getDiscriminatorFieldMap() {
        return discriminatorFieldMap;
    }

    public boolean isDiscriminatorSpecified() {
        return discriminatorSpecified;
    }

    public enum Operation {
        READ,
        CREATE,
        UPDATE
    }

    public static class EntityClassSpec<E> {
        static final EntityClassSpec MERGE = new EntityClassSpec<>(Object.class, null);

        private Class<E> entityClass;
        private String alias;
        private boolean suppressLocalisation = false;

        public EntityClassSpec(Class<E> entityClass) {
            this.entityClass = entityClass;
        }

        public EntityClassSpec(Class<E> entityClass, String alias) {
            this.entityClass = entityClass;
            this.alias = entityClass == null || entityClass.getName().equals(alias) ? null : alias;
        }

        public EntityClassSpec(Class<E> entityClass, String alias, boolean suppressLocalisation) {
            this.entityClass = entityClass;
            this.alias = entityClass == null || entityClass.getName().equals(alias) ? null : alias;
            this.suppressLocalisation = suppressLocalisation;
        }

        public Class<E> getEntityClass() {
            return entityClass;
        }

        public String getAlias() {
            return (alias == null) ? entityClass.getSimpleName() : alias;
        }

        public boolean isSuppressLocalisation() {
            return suppressLocalisation;
        }

        /**
         * Obtains the value to be used as an external discriminator string for an entity subclass.
         * This will be any discriminator value specified in the hibernate config.
         * If none is specified, hibernate uses the full class name as a discriminator, which will be replaced by the entity alias (which in turn defaults to the base class name).
         *
         * @return the discriminator value
         */
        public String getExternalDiscriminatorValue() {
            String hibernateDiscriminatorValue = ReflectorFactory.getInstance().getReflector().getDiscriminatorValue(entityClass);
            return hibernateDiscriminatorValue == null || hibernateDiscriminatorValue.equals(entityClass.getName()) ? getAlias() : hibernateDiscriminatorValue;
        }
    }

    public static class MapperBuilder<T> {
        private EntityClassSpec<T> baseClassSpec;
        private Map<String, EntityClassSpec<T>> subClassSpecs = new HashMap<>();
        private String externalDiscriminatorName;

        private List<FieldMapProvider> fieldMapProviders = new ArrayList<>();

        private MapperBuilder(EntityClassSpec<T> baseClassSpec) {
            this.baseClassSpec = baseClassSpec;
        }

        public MapperBuilder subClass(EntityClassSpec<T> subClassSpec) {
            if (!baseClassSpec.entityClass.isAssignableFrom(subClassSpec.entityClass)) {
                throw new EntityMapperException(subClassSpec.entityClass.getName() + "is not a subclass of " + baseClassSpec.entityClass.getName());
            }
            subClassSpecs.put(subClassSpec.getExternalDiscriminatorValue(), subClassSpec);
            return this;
        }

        public MapperBuilder discriminateBy(String externalDiscriminatorName) {
            this.externalDiscriminatorName = externalDiscriminatorName;
            return this;
        }

        public MapperBuilder map(FieldMapProvider... providers) {
            fieldMapProviders.addAll(Arrays.asList(providers));
            return this;
        }

        public EntityMapper build() {
            return new EntityMapper<>(baseClassSpec, subClassSpecs, externalDiscriminatorName,
                    fieldMapProviders.stream().flatMap(f -> f.getFieldMaps().stream()).collect(Collectors.toList()));
        }
    }
}