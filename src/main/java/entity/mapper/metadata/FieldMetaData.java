package entity.mapper.metadata;

import java.util.Collections;
import java.util.List;

/**
 * Represents a data about a particular field as an immutable class
 */
public class FieldMetaData {

	public enum Attribute {
		SIMPLE,
		ONE_TO_MANY,
		MANY_TO_ONE,
		COMPONENT,
		PRIMARY_KEY,
		COLLECTION,
		INDEXED,
		ORPHAN_DELETE
	}

	private final String internalName;

	private final String externalName;

	private final ClassContext classContext;

	private final List<Attribute> attributes;

	private final int length;

	public FieldMetaData(String internalName, String externalName, ClassContext classContext, List<Attribute> attributes, int length) {
		this.internalName = internalName;
		this.externalName = externalName;
		this.classContext = classContext;
		this.attributes = Collections.unmodifiableList(attributes);
		this.length = length;
	}

	/**
	 * The representation used to identify the field internal to the owning application
	 * @return - representation used to identify the field internal to the application
	 */
	public String getInternalName() {
		return internalName;
	}

	/**
	 * The representation used to identify the field external to the owning application
	 * @return - representation used to identify the field external to the owning application
	 */
	public String getExternalName() {
		return externalName;
	}

	/**
	 * Context that the class lays in
	 * @return - will return the ClassContext
	 */
	public ClassContext getClassContext() {
		return classContext;
	}

	/**
	 * Extra attributes used to describe the field
	 * @return - attributes used to describe the field
	 */
	public List<Attribute> getAttributes() {
		return attributes;
	}

    public int getLength() {
        return length;
    }

    @Override
	public String toString() {
		return String.format("[internal: %s, external: %s, attributes: %s, context: %s]", internalName, externalName, attributes, classContext);
	}
}
