package entity.session;

public interface SessionContext {
    Session getSession();

    void flush() throws PersistenceException;

    void flushEvictions() throws PersistenceException;

    void clear();

    void evict(Object entity);

    void putContext(Object key, Object value);

    void close();

    Object getContext(Object key);
}
