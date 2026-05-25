package com.forfun.codel_ineage.collector.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Data
@Builder
public class CapturedSql {
    private String rawSql;
    private List<String> params;
    private DbType dbType;
    private String connUrl;
    private String callerMethodId;    // resolved from stack trace
    private String callerClassName;
    private String callerMethodName;
    private long timestamp;
    private long durationNs;
    private String threadName;

    public static CapturedSql capture(String sql, List<?> params, String connUrl,
                                       String callerClass, String callerMethod) {
        return CapturedSql.builder()
                .rawSql(sql)
                .params(params != null ? params.stream().map(Object::toString).toList() : List.of())
                .dbType(DbType.fromJdbcUrl(connUrl))
                .connUrl(connUrl)
                .callerClassName(callerClass)
                .callerMethodName(callerMethod)
                .timestamp(Instant.now().toEpochMilli())
                .threadName(Thread.currentThread().getName())
                .build();
    }
}
