package entity.mapper.metadata;

import entity.mapper.fieldmaps.EntityFieldMap;
import entity.mapper.metadata.reflector.MetaDataCollector;
import javafx.beans.property.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is the default way of finding reflection information from classes.
 */
public class DefaultReflector implements Reflector {

    private List<MetaDataCollector> reflectorList = new ArrayList<>();

    protected void addReflector(MetaDataCollector reflector) {
        reflectorList.add(reflector);
    }

    @Override
    public FieldMetaDataList getFieldListForClass(ClassContext clazzContext) {

        FieldMetaDataList currentList = new FieldMetaDataList();

        for (MetaDataCollector reflector : reflectorList) {
            currentList = reflector.buildFieldMetaDataList(clazzContext, currentList);
        }

        return currentList;
    }

    @Override
    public FieldMetaData getFieldMetaData(Class entityClass, String internalFieldName) {
        FieldMetaData fieldMetaData = null;
        for (String fieldPart : internalFieldName.split("\\.")) {
            FieldMetaDataList metaDataList;
            if (fieldMetaData == null) {
                metaDataList = getFieldListForClass(new ClassContext("", entityClass));
                if (metaDataList != null && entityClass.getSuperclass() != null) {
                    FieldMetaDataList superClassMetaDataList = getFieldListForClass(new ClassContext("", entityClass.getSuperclass()));
                    if (superClassMetaDataList != null) {
                        metaDataList = metaDataList.merge(superClassMetaDataList);
                    }
                }
            } else {
                metaDataList = getFieldListForClass(fieldMetaData.getClassContext());
            }
            if (metaDataList == null) {
                return null;
            }
            fieldMetaData = metaDataList.getByInternalName(fieldPart);
        }
        return fieldMetaData;
    }

    @Override
    public boolean isLocalisable(Class entityClass) {
        return false;
    }

    @Override
    public boolean isVersioned(Class entityClass) {
        return false;
    }

    @Override
    public boolean isPolymorphic(Class entityClass) {
        return false;
    }

    @Override
    public String getDiscriminatorName(Class entityClass) {
        return null;
    }

    @Override
    public String getDiscriminatorValue(Class entitySubClass) {
        return null;
    }

    @Override
    public Property getFieldProperty(EntityFieldMap fieldMap) {
        return null;
    }
}
