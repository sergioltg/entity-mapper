package entity.mapper.fieldmaps;

/**
 * Map to handle a subclass discriminator
 */
public class DiscriminatorFieldMap extends SimpleFieldMap {
    // By default in HQL, the discriminator field is accessed by the special property "class". Note that this default value will never be used to reference a value object property.
    private static final String DEFAULT_INTERNAL_DISCRIMINATOR_NAME = "class";

    // Default json field for discriminator if one is not specified or if the hibernate default "class" is specified (cannot use "class" as it is a typescript reserved word)
    private static final String DEFAULT_EXTERNAL_DISCRIMINATOR_NAME = "subclass";

    private boolean hibernateProperty;

    public DiscriminatorFieldMap(String internalDiscriminatorName, String externalDiscriminatorName, boolean hibernateProperty) {
        super(internalDiscriminatorName == null ? DEFAULT_INTERNAL_DISCRIMINATOR_NAME : internalDiscriminatorName,
                externalDiscriminatorName == null || externalDiscriminatorName.equals(DEFAULT_INTERNAL_DISCRIMINATOR_NAME) ? DEFAULT_EXTERNAL_DISCRIMINATOR_NAME : externalDiscriminatorName);
        this.hibernateProperty = hibernateProperty;
    }

    public boolean isHibernateProperty() {
        return hibernateProperty;
    }
}