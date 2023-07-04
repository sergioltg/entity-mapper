package entity.mapper.metadata.reflector;

import entity.mapper.metadata.ClassContext;
import entity.mapper.metadata.FieldMetaDataList;

public interface MetaDataCollector {

	FieldMetaDataList buildFieldMetaDataList(ClassContext clazz, FieldMetaDataList currentList);

}
