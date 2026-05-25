package com.forfun.codel_ineage.analyzer.detector;

import java.util.*;

/**
 * Detects Spring MVC entry points:
 * @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping
 */
public class SpringMvcDetector implements EntryPointDetector {

    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "RequestMapping", "GetMapping", "PostMapping",
            "PutMapping", "DeleteMapping", "PatchMapping"
    );

    @Override
    public String framework() { return "Spring"; }

    @Override
    public List<EntryPointInfo> detect(List<String> classAnnotations,
                                       List<String> methodAnnotations,
                                       String className,
                                       String methodName) {
        List<EntryPointInfo> results = new ArrayList<>();

        for (String ann : methodAnnotations) {
            for (String mappingAnn : MAPPING_ANNOTATIONS) {
                if (ann.startsWith(mappingAnn)) {
                    String httpMethod = extractHttpMethod(ann);
                    String path = extractPath(ann);
                    results.add(EntryPointInfo.builder()
                            .type("HTTP")
                            .path(path)
                            .httpMethod(httpMethod)
                            .framework("Spring")
                            .isClassLevel(false)
                            .build());
                }
            }
        }
        return results;
    }

    private String extractHttpMethod(String annotation) {
        if (annotation.contains("PostMapping")) return "POST";
        if (annotation.contains("GetMapping")) return "GET";
        if (annotation.contains("PutMapping")) return "PUT";
        if (annotation.contains("DeleteMapping")) return "DELETE";
        if (annotation.contains("PatchMapping")) return "PATCH";
        if (annotation.contains("RequestMapping")) return null; // method-level can specify
        return "GET";
    }

    private String extractPath(String annotation) {
        // Parse value/path from annotation string like "PostMapping(value=\"/api/login\")"
        int valueStart = annotation.indexOf("(\"");
        if (valueStart < 0) {
            valueStart = annotation.indexOf("(value=\"");
            if (valueStart < 0) return null;
            valueStart += 7;
        } else {
            valueStart += 2;
        }
        int valueEnd = annotation.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return annotation.substring(valueStart, valueEnd);
    }
}
