package entity.mapper.metadata;

/**
 * Class that will perform the task of reflecting over an object using registered
 * au.com.afjsoftware.dataserver.metadata.MetaDataReflector reflectors to build a list of fields that is contained
 * in the Object provided.
 */
public class ReflectorFactory {

	private static final Object lock = new Object();
	private static volatile ReflectorFactory instance;

	private Reflector reflector;

	private ReflectorFactory() {}

	public static ReflectorFactory getInstance() {

		if(instance == null) {
			synchronized (lock) {

				if(instance == null) {
					instance = new ReflectorFactory();
				}

			}
		}

		return instance;

	}

	/**
	 * Register an reflector. Order in important as an reflector may modify exsiting entries.
	 * @param reflector - the reflector to add
	 */
	public synchronized void register(Reflector reflector) {
		this.reflector = reflector;
	}

	/**
	 * Will get the registered reflector
	 * @return - the registered reflector
	 */
	public synchronized Reflector getReflector() {
		return this.reflector;
	}

}
