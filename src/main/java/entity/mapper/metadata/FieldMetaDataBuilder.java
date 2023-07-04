package entity.mapper.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Will assist in building FieldMetaData objects by allowing a clone of an existing FieldMetaData. Also will apply
 * simple business rules to new FieldMetaData objects.
 */
public class FieldMetaDataBuilder {

	private String internalName = null;
	private String externalName = null;
	private ClassContext classContext = null;
	private int length;
	private List<FieldMetaData.Attribute> attributes = new ArrayList<>();

	public FieldMetaDataBuilder () {}

	public FieldMetaDataBuilder(FieldMetaData fieldMetaData) {

		if(fieldMetaData != null) {
			this.internalName = fieldMetaData.getInternalName();
			this.externalName = fieldMetaData.getExternalName();
			this.classContext = fieldMetaData.getClassContext();
			this.attributes = fieldMetaData.getAttributes();
		}

	}

	/**
	 * Use this as the internalName
	 * @param internalName - the internal name to use
	 */
	public void withInternalName(String internalName) {
		this.internalName = internalName;
	}

	/**
	 * Use this as the externalName
	 * @param externalName - the external name to use
	 */
	public void withExternalName(String externalName) {
		this.externalName = externalName;
	}

	/**
	 * Use this as the attributes. Will overwrite any existing attributes.
	 * @param attributes - the attributes to use
	 */
	public void withAttributes(List<FieldMetaData.Attribute> attributes) {
		this.attributes = attributes;
	}

	public void withLength(int length) {
        this.length = length;
    }

	/**
	 * Will add an attribute to the already existing attributes.
	 * @param attribute - the attribute to add
	 */
	public void addAttribute(FieldMetaData.Attribute attribute) {
		attributes.add(attribute);
	}

	/**
	 * Will add a classContext
	 * @param classContext
	 */
	public void withClassContext(ClassContext classContext) {
		this.classContext = classContext;
	}

	public void addClassToContext(String fieldName, Class clazz) {
		if(this.classContext == null) {
			this.classContext = new ClassContext(fieldName, clazz);
		} else {
			this.classContext = this.classContext.addClass(fieldName, clazz);
		}
	}

	/**
	 * Will build the FieldMetaData object reflecting the state of the FieldMetaDataBuilder
	 * @return - a new FieldMetaData object
	 */
	public FieldMetaData build() {

		if(externalName == null) {
			externalName = internalName;
		}

		return new FieldMetaData(internalName, externalName, classContext, attributes, length);
	}

}
