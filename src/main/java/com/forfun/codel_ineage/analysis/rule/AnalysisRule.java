package com.forfun.codel_ineage.analysis.rule;

import java.util.List;

public interface AnalysisRule {
    String id();
    String name();
    String severity();
    String category();
    List<Finding> analyze(Target target);

    record Target(String appId, String methodId, String tableName, String columnName) {
        public static Target app(String appId) {
            return new Target(appId, null, null, null);
        }
        public static Target method(String methodId) {
            return new Target(null, methodId, null, null);
        }
        public static Target table(String tableName) {
            return new Target(null, null, tableName, null);
        }
    }
}
