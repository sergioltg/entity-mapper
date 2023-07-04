package entity.mapper;

import entity.mapper.datamap.DataMap;
import entity.mapper.fieldmaps.CollectionMap;

import java.util.List;

public class EntityCollectionDataMapping implements EntityDataMapping {
    private CollectionMap fieldMap;
    private List<DataMap> dataMap;

    public EntityCollectionDataMapping(CollectionMap fieldMap, List<DataMap> dataMap) {
        this.fieldMap = fieldMap;
        this.dataMap = dataMap;
    }

    public CollectionMap getFieldMap() {
        return fieldMap;
    }

    public List<DataMap> getDataMap() {
        return dataMap;
    }
}
