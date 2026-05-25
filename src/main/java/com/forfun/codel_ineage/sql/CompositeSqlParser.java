package com.forfun.codel_ineage.sql;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
public class CompositeSqlParser implements SqlParser {

    private final MyBatisSqlParser myBatisParser;
    private final JpaSqlParser jpaParser;
    private final MyBatisPlusSqlParser myBatisPlusParser;

    public CompositeSqlParser(MyBatisSqlParser myBatisParser,
                              JpaSqlParser jpaParser,
                              MyBatisPlusSqlParser myBatisPlusParser) {
        this.myBatisParser = myBatisParser;
        this.jpaParser = jpaParser;
        this.myBatisPlusParser = myBatisPlusParser;
    }

    @Override
    public List<SqlRelations> parse(ParseTask task) {
        List<SqlRelations> results = new ArrayList<>();
        results.addAll(myBatisParser.parse(task));
        results.addAll(jpaParser.parse(task));
        results.addAll(myBatisPlusParser.parse(task));
        return results;
    }
}
