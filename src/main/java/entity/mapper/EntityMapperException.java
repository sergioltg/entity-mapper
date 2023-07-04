package entity.mapper;

/**
 * Indicates any failure to Entity Resource Mapper
 */
public class EntityMapperException extends RuntimeException {
    public EntityMapperException() {
        super();
    }

    public EntityMapperException(String message) {
        super(message);
    }

    public EntityMapperException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityMapperException(Throwable cause) {
        super(cause);
    }
}
