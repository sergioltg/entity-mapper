package entity.mapper.datamap;

import java.util.Map;

/**
 * Not actually used in the DataMap (which extends HashMap),
 * but required for ComponentToCsvFieldConverter for holding mapped values in a stream before adding to the map
 */
public final class DataMapEntry implements Map.Entry<String, Object> {
    private String key;
    private Object value;

    public DataMapEntry(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Object setValue(Object value) {
        Object oldValue = this.value;
        this.value = value;
        return oldValue;
    }
}
