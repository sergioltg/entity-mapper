package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;
import entity.mapper.metadata.ClassContext;
import entity.mapper.metadata.FieldMetaData;
import entity.mapper.metadata.FieldMetaDataList;
import entity.mapper.metadata.ReflectorFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide a provider that will create the mapping using reflection
 */
public class ReflectionMap {

    private static final Logger LOGGER = Logger.getLogger(ReflectionMap.class.getName());

    public static FieldMapProvider reflection(Class objectClass) {
        return new Provider(objectClass);
    }

    private static class Provider implements FieldMapProvider {
        private Class objectClass;

        public Provider(Class objectClass) {
            this.objectClass = objectClass;
        }

        @Override
        public Collection<? extends FieldMap> getFieldMaps() {

            ReflectorFactory reflectorFactory = ReflectorFactory.getInstance();
            ClassContext classContext = new ClassContext("", objectClass);
            FieldMetaDataList metaDataList = reflectorFactory.getReflector().getFieldListForClass(classContext);

            final int maximumDepth = 4;
            int currentDepth = 0;
            List<FieldMap> fieldMap = new ArrayList<>();
            List<FieldMapProvider> fieldMapProviders = translateMetaDataToFieldMap(classContext, reflectorFactory, metaDataList, currentDepth, maximumDepth);
            fieldMapProviders.forEach(provider -> {
                fieldMap.addAll(provider.getFieldMaps());
            });
            return fieldMap;

        }

        private List<FieldMapProvider> translateMetaDataToFieldMap(final ClassContext classContext, ReflectorFactory reflectorFactory, FieldMetaDataList metaDataList, final int depth, final int maximumDepth) {
            final int nextDepth = depth + 1;
            List<FieldMapProvider> fieldMaps = new ArrayList<>();
            metaDataList.getMembers().forEach((metaData) -> {
                try {
                    if (metaData.getAttributes().contains(FieldMetaData.Attribute.COLLECTION) && depth < maximumDepth) {
                        if (metaData.getAttributes().contains(FieldMetaData.Attribute.SIMPLE)) {
                            // TODO - Need to complete
                        } else {
                            FieldMetaDataList childMetaDataList = reflectorFactory.getReflector().getFieldListForClass(metaData.getClassContext());
                            List<FieldMapProvider> childFieldMapList = translateMetaDataToFieldMap(metaData.getClassContext(), reflectorFactory, childMetaDataList, nextDepth, maximumDepth);

                            fieldMaps.add(CollectionMap.collection(metaData.getInternalName(), CollectionMap.Mode.LAZY,
                                    EntityMapper.entity(metaData.getClassContext().getTail().getClazz()).map(
                                            childFieldMapList.toArray(new FieldMapProvider[childFieldMapList.size()])
                                    ).build())
                            );
                        }
                    } else if ((metaData.getAttributes().contains(FieldMetaData.Attribute.COMPONENT) || metaData.getAttributes().contains(FieldMetaData.Attribute.MANY_TO_ONE) || metaData.getAttributes().contains(FieldMetaData.Attribute.ONE_TO_MANY)) && depth < maximumDepth) {
                        FieldMetaDataList childMetaDataList = reflectorFactory.getReflector().getFieldListForClass(metaData.getClassContext());
                        List<FieldMapProvider> childFieldMapList = translateMetaDataToFieldMap(metaData.getClassContext(), reflectorFactory, childMetaDataList, nextDepth, maximumDepth);

                        fieldMaps.add(ComponentMap.component(metaData.getInternalName(),
                                EntityMapper.entity(metaData.getClassContext().getTail().getClazz()).map(
                                        childFieldMapList.toArray(new FieldMapProvider[childFieldMapList.size()])
                                ).build())
                        );
                    } else if (metaData.getAttributes().contains(FieldMetaData.Attribute.SIMPLE) && depth < maximumDepth) {
                        fieldMaps.add(SimpleFieldMap.fieldMap(metaData.getInternalName(), metaData.getExternalName(), null, false, metaData.getAttributes().contains(FieldMetaData.Attribute.PRIMARY_KEY), false, FieldMap.Access.FULL, false, null));
                    }
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "Could not resolve " + metaData, t);
                    throw t;
                }
            });
            return fieldMaps;
        }
    }
}