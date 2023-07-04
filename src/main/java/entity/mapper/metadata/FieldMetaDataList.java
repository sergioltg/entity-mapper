package entity.mapper.metadata;

import java.util.*;

/**
 * Will contain a collection of FieldMetadata objects describing a class in an immutable structure
 */
public class FieldMetaDataList {

	private final List<FieldMetaData> fieldMetaDataList;
	private final Map<String, FieldMetaData> fieldMetaDataMap;

	public FieldMetaDataList() {

		this.fieldMetaDataList = Collections.EMPTY_LIST;
		this.fieldMetaDataMap = Collections.EMPTY_MAP;

	}

	public FieldMetaDataList(List<FieldMetaData> fieldMetaDataList) {

		this.fieldMetaDataList = Collections.unmodifiableList(new ArrayList<>(fieldMetaDataList));
		this.fieldMetaDataMap = Collections.unmodifiableMap(mapFieldMetaData(fieldMetaDataList));

	}

	public FieldMetaData getByInternalName(String internalName) {

		return this.fieldMetaDataMap.get(internalName);

	}

	/**
	 * Will merge this list with the updateList provided, using the internalName as the key for each FieldMetaData elements.
	 * Rules 1) If an element is present in the updateList AND this list, then the new list will contain the updateList version.
	 *       2) If an element is present only in this list, then the new list will contain this element.
	 *       3) If an element is present only in the updateList, then the new list will contain this element.
	 * @param updateList - the list to merge this with
	 * @return - the new list
	 */
	public FieldMetaDataList merge(FieldMetaDataList updateList) {

		List<FieldMetaData> resultList = new ArrayList<>();
		Set<String> resultListFieldsAdded = new HashSet<>();

		// 1) copy entry from 'update list' if present to the 'new list'
		// 2) otherwise copy 'this list' version to the 'new list'
		for(Map.Entry<String, FieldMetaData> entry : this.fieldMetaDataMap.entrySet()) {

			String key = entry.getKey();
			FieldMetaData value = entry.getValue();

			if(updateList.fieldMetaDataMap.containsKey(key)) {
				resultList.add(updateList.fieldMetaDataMap.get(key));
			} else {
				resultList.add(value);
			}
			resultListFieldsAdded.add(entry.getKey());

		}

		// copy from 'update list' to 'new list', all entries that aren't already in 'this list'
		for(Map.Entry<String, FieldMetaData> entry : updateList.fieldMetaDataMap.entrySet()) {

			String key = entry.getKey();
			FieldMetaData value = entry.getValue();

			if(!resultListFieldsAdded.contains(key)) {
				resultList.add(value);
				resultListFieldsAdded.add(key);
			}

		}

		return new FieldMetaDataList(resultList);

	}

	/**
	 * Will get all the members of this list
	 * @return - the members of this list
	 */
	public List<FieldMetaData> getMembers() {
		return this.fieldMetaDataList;
	}

	protected Map<String, FieldMetaData> mapFieldMetaData(List<FieldMetaData> fieldMetaDataList) {

		LinkedHashMap<String, FieldMetaData> map = new LinkedHashMap<>();
		for(FieldMetaData data : fieldMetaDataList) {
			map.put(data.getInternalName(), data);
		}
		return map;

	}

	public boolean isEmpty() {
		return fieldMetaDataList.isEmpty();
	}

}
