package entity.mapper.metadata;

import entity.mapper.fieldmaps.EntityFieldMap;
import javafx.beans.property.Property;

public interface Reflector {

    /**
     * Will return a FieldMetaDataList that contains information about the fields on the passed in class.
     *
     * @param clazzContext - A class context to retrieve field information for. The target is the tail of the list.
     * @return - FieldMetaDataList describing the fields on the clazzContext
     */
    FieldMetaDataList getFieldListForClass(ClassContext clazzContext);

    FieldMetaData getFieldMetaData(Class entityClass, String internalFieldName);

    boolean isLocalisable(Class entityClass);

    boolean isVersioned(Class entityClass);

    boolean isPolymorphic(Class entityClass);

    String getDiscriminatorName(Class entityClass);

    String getDiscriminatorValue(Class entitySubClass);

    Property getFieldProperty(EntityFieldMap fieldMap);
}
