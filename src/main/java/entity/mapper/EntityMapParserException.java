package entity.mapper;

/**
 * Indicates any failure to parse an Entity Resource Map
 */
public class EntityMapParserException extends RuntimeException {
    public EntityMapParserException() {
        super();
    }

    public EntityMapParserException(String message) {
        super(message);
    }

    public EntityMapParserException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityMapParserException(Throwable cause) {
        super(cause);
    }
}
