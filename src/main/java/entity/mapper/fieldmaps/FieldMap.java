package entity.mapper.fieldmaps;

import entity.mapper.EntityMapper;

/**
 * Defines a field map
 */
public abstract class FieldMap {
    public final static String PASSWORD_MASK = "������";

    protected String internalFieldName;
    protected String externalFieldName;
    protected Access access;
    protected String pluginName;
    protected boolean isAttribute;
    protected EntityMapper parentEntityMapper;
    protected boolean exclusive;

    public enum Access {
        FULL,
        READONLY,
        FINAL,
        WRITEONLY,
        CREATEONLY,
        SENSITIVE
    }

    public FieldMap(String internalFieldName, String externalFieldName, String pluginName, boolean isAttribute, Access access, boolean exclusive) {
        this.internalFieldName = internalFieldName;
        this.externalFieldName = externalFieldName;
        this.pluginName = pluginName;
        this.isAttribute = isAttribute;
        this.access = access;
        this.exclusive = exclusive;
    }

    /**
     * The field name from (Hibernate) Value Object
     *
     * @return String
     */
    public String getInternalFieldName() {
        return internalFieldName;
    }

    /**
     * The field name that can be be represented by external concepts, such as Json etc.
     *
     * @return String
     */
    public String getExternalFieldName() {
        return externalFieldName;
    }

    /**
     * The name of the plugin which manages this field as an attribute
     *
     * @return String
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * @return true if a plugin name is explicitly specified for this field map (as opposed to being inherited from parent)
     */
    public boolean isAttribute() {
        return isAttribute;
    }

    /**
     * @return true if a field is exclusive (not cloneable)
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Specifies the access mode of the field
     *
     * @return Access
     */
    public Access getAccess() {
        return access;
    }

    public EntityMapper getParentEntityMapper() {
        return parentEntityMapper;
    }

    public void setParentEntityMapper(EntityMapper parentEntityMapper) {
        this.parentEntityMapper = parentEntityMapper;
    }

    public String getInternalPath() {
        return getParentEntityMapper() == null || getParentEntityMapper().getParentFieldMap() == null ? getInternalFieldName() : (getParentEntityMapper().getParentFieldMap().getInternalPath() + "." + getInternalFieldName());
    }


    public String getExternalPath() {
        return getParentEntityMapper() == null || getParentEntityMapper().getParentFieldMap() == null ? getExternalFieldName() : (getParentEntityMapper().getParentFieldMap().getExternalPath() + "." + getExternalFieldName());
    }

    /**
     * Determines whether an UPDATE/CREATE operation is allowed for the access level of this field map
     *
     * @param operation
     * @return
     */
    public boolean operationAllowed(EntityMapper.Operation operation) {
        // Check whether plugin is enabled
        switch (operation) {
            case READ:
                return getAccess() != Access.WRITEONLY && getAccess() != Access.CREATEONLY;
            case CREATE:
                return getAccess() != Access.READONLY;
            case UPDATE:
                return getAccess() == Access.FULL || getAccess() == Access.WRITEONLY || getAccess() == Access.SENSITIVE;
            default:
                return false;
        }
    }
}
