package entity.mapper.datamap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A hash map for storing data to be mapped to/from a nested interchange format such as json.
 * <p>
 * All keys are strings, and values are either a json-compatible primitive types or a nested DataMap.
 * The standard put, get, containsKey, and remove methods are overridden to support access to nested keys via period-separated paths.
 * <p>
 * Also provides methods for creating a DataMap from json or from a value object.
 */
public class DataMap extends HashMap<String, Object> {

    private DataMap parent;

    public DataMap() {
        super();
    }

    public DataMap(Map<? extends String, ?> m) {
        super(m);
    }

    /**
     * Parse a DataMap from the given json string.
     *
     * @param json The json string
     * @return The parsed DataMap
     * @throws IOException
     */
    public static DataMap fromJson(String json) throws IOException {
        return getObjectMapper().readValue(json, DataMap.class);
    }

    /**
     * Parse a DataMap from an input stream.
     *
     * @param inputStream The input stream
     * @return The parsed DataMap
     * @throws IOException
     */
    public static DataMap fromJson(InputStream inputStream) throws IOException {
        return getObjectMapper().readValue(inputStream, DataMap.class);
    }

    /**
     * Returns the map as a json
     *
     * @return
     * @throws JsonProcessingException
     */
    public String toJson() throws JsonProcessingException {
        return getObjectMapper().writeValueAsString(this);
    }

    /**
     * Parse a DataMap from a json string retrieved from the given url.
     *
     * @param src The url
     * @return The parsed DataMap
     * @throws IOException
     */
    public static DataMap fromJson(URL src) throws IOException {
        return getObjectMapper().readValue(src, DataMap.class);
    }

    /**
     * Create a DataMap from the given value object.
     *
     * @param object The value object
     * @return The DataMap
     * @throws IllegalArgumentException
     */
    public static DataMap fromObject(Object object) throws IllegalArgumentException {
        return getObjectMapper().convertValue(object, DataMap.class);
    }

    private static ObjectMapper getObjectMapper() {
        return new ObjectMapper()
                .registerModule(new SimpleModule().addAbstractTypeMapping(Map.class, DataMap.class))
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String && ((String) key).contains(".")) {
            int i = ((String) key).indexOf('.');
            Object component = super.get(((String) key).substring(0, i));
            return component != null && component instanceof DataMap ? ((DataMap) component).get(((String) key).substring(i + 1)) : null;
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String && ((String) key).contains(".")) {
            int i = ((String) key).indexOf('.');
            Object component = super.get(((String) key).substring(0, i));
            return component != null && component instanceof DataMap && ((DataMap) component).containsKey(((String) key).substring(i + 1));
        }
        return super.containsKey(key);
    }

    @Override
    public Object put(String key, Object value) {
        if (key.contains(".")) {
            int i = key.indexOf('.');
            String componentKey = key.substring(0, i);
            Object component = super.get(componentKey);
            if (component == null || !(component instanceof DataMap)) {
                component = new DataMap();
                super.put(componentKey, component);
            }
            return ((DataMap) component).put(key.substring(i + 1), value);
        }
        return super.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        if (key instanceof String && ((String) key).contains(".")) {
            int i = ((String) key).indexOf('.');
            Object component = super.get(((String) key).substring(0, i));
            return component != null && component instanceof DataMap ? ((DataMap) component).remove(((String) key).substring(i + 1)) : null;
        }
        return super.remove(key);
    }

    public DataMap with(String key, Object value) {
        put(key, value);
        return this;
    }

    public DataMap withDerived(String key, String format, String derivedFromKey) {
        put(key, String.format(format, get(derivedFromKey)));
        return this;
    }

    public DataMap without(String key) {
        remove(key);
        return this;
    }

    public DataMap forEachDataMap(String key, Consumer<DataMap> action) {
        ((List<DataMap>) get(key)).forEach(action);
        return this;
    }

    public DataMap getParent() {
        return parent;
    }

    public void setParent(DataMap parent) {
        this.parent = parent;
    }
}
