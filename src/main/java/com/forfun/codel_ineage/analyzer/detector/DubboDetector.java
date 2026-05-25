package com.forfun.codel_ineage.analyzer.detector;

import java.util.*;

/**
 * Detects Dubbo RPC entry points:
 * @DubboService (class-level), @ShenyuDubboService (ShenYu variant)
 */
public class DubboDetector implements EntryPointDetector {

    private static final Set<String> DUBBO_SERVICE_ANNOTATIONS = Set.of(
            "DubboService", "ShenyuDubboService"
    );

    @Override
    public String framework() { return "Dubbo"; }

    @Override
    public List<EntryPointInfo> detect(List<String> classAnnotations,
                                       List<String> methodAnnotations,
                                       String className,
                                       String methodName) {
        List<EntryPointInfo> results = new ArrayList<>();

        // Dubbo @Service/@DubboService is class-level
        for (String ann : classAnnotations) {
            for (String dubboAnn : DUBBO_SERVICE_ANNOTATIONS) {
                if (ann.equals(dubboAnn) || ann.startsWith(dubboAnn + "(")) {
                    results.add(EntryPointInfo.builder()
                            .type("RPC")
                            .path(className)
                            .httpMethod(methodName)
                            .framework("Dubbo")
                            .isClassLevel(true)
                            .build());
                }
            }
        }
        return results;
    }
}
