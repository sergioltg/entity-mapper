package entity.mapper.fieldmaps;

import java.util.Collection;

/**
 * Provides a list of fieldMaps
 */
public interface FieldMapProvider {

    Collection<? extends FieldMap> getFieldMaps();

}
