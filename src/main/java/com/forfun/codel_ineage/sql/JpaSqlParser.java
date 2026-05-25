package com.forfun.codel_ineage.sql;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class JpaSqlParser implements SqlParser {

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?:FROM|JOIN|INTO|UPDATE|INSERT\\s+INTO)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    @Override
    public List<SqlRelations> parse(ParseTask task) {
        // JPA @Query value extraction not yet implemented.
        return List.of();
    }
}
