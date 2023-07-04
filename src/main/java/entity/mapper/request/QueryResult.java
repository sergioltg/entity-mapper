package entity.mapper.request;

import entity.session.SessionContext;

public interface QueryResult {
    String marshall(SessionContext context);
}
