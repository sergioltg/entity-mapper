package entity.mapper.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassContext {

	final private List<ClassNode> pathToClazz;

	public ClassContext(String fieldName, Class targetClazz) {

		this.pathToClazz = Collections.singletonList(new ClassNode(fieldName, targetClazz));

	}

	public ClassContext(List<ClassNode> pathToClazz) {

		this.pathToClazz = pathToClazz;

	}

	public List<ClassNode> getPathToClazz() {

		return pathToClazz;

	}

	public ClassNode getHead() {

		if(pathToClazz.size() > 0) {
			return pathToClazz.get(0);
		} else {
			return null;
		}

	}

	public ClassNode getTail() {

		if(pathToClazz.size() > 0) {
			return pathToClazz.get(pathToClazz.size() - 1);
		} else {
			return null;
		}

	}

	public ClassContext addClass(String fieldName, Class clazz) {

		List<ClassNode> newList = new ArrayList<>(pathToClazz);
		newList.add(new ClassNode(fieldName, clazz));
		return new ClassContext(newList);

	}

	@Override
	public String toString() {

		return pathToClazz.toString();

	}

	public static class ClassNode {

		private final Class clazz;
		private final String propertyName;

		public ClassNode(String propertyName, Class clazz) {
			this.propertyName = propertyName;
			this.clazz = clazz;
		}

		public Class getClazz() {
			return clazz;
		}

		public String getPropertyName() {
			return propertyName;
		}

		@Override
		public String toString() {

			return String.format("[%s, %s]", propertyName, clazz.getName());

		}
	}

}
