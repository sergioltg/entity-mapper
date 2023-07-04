package entity.mapper;

public interface Attributed {
    void setAttribute(String id, Object attribute);

    Object getAttribute(String id);

    Object removeAttribute(String id);
}
