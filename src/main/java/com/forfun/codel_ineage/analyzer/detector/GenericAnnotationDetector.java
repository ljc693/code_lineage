package com.forfun.codel_ineage.analyzer.detector;

import java.util.*;

/**
 * Configurable detector for custom framework annotations.
 * Accepts a set of annotation names at construction time.
 */
public class GenericAnnotationDetector implements EntryPointDetector {

    private final String frameworkName;
    private final String entryType;
    private final Set<String> annotationNames;
    private final boolean detectOnClass;

    public GenericAnnotationDetector(String frameworkName, String entryType,
                                      Set<String> annotationNames, boolean detectOnClass) {
        this.frameworkName = frameworkName;
        this.entryType = entryType;
        this.annotationNames = annotationNames;
        this.detectOnClass = detectOnClass;
    }

    @Override
    public String framework() { return frameworkName; }

    @Override
    public List<EntryPointInfo> detect(List<String> classAnnotations,
                                       List<String> methodAnnotations,
                                       String className,
                                       String methodName) {
        List<EntryPointInfo> results = new ArrayList<>();

        // Check class-level annotations
        if (detectOnClass) {
            for (String ann : classAnnotations) {
                if (matches(ann)) {
                    results.add(EntryPointInfo.builder()
                            .type(entryType)
                            .path(className)
                            .httpMethod(methodName)
                            .framework(frameworkName)
                            .isClassLevel(true)
                            .build());
                }
            }
        }

        // Check method-level annotations
        for (String ann : methodAnnotations) {
            if (matches(ann)) {
                results.add(EntryPointInfo.builder()
                        .type(entryType)
                        .path(extractPath(ann))
                        .httpMethod(extractHttpMethod(ann))
                        .framework(frameworkName)
                        .isClassLevel(false)
                        .build());
            }
        }
        return results;
    }

    private boolean matches(String annotation) {
        String name = annotation.contains("(") ? annotation.substring(0, annotation.indexOf('(')) : annotation;
        return annotationNames.contains(name);
    }

    private String extractPath(String annotation) {
        int valueStart = annotation.indexOf("(\"");
        if (valueStart < 0) {
            valueStart = annotation.indexOf("(value=\"");
            if (valueStart < 0) return null;
            valueStart += 7;
        } else {
            valueStart += 2;
        }
        int valueEnd = annotation.indexOf('"', valueStart + 1);
        return valueEnd > 0 ? annotation.substring(valueStart, valueEnd) : null;
    }

    private String extractHttpMethod(String annotation) {
        if (annotation.contains("GET")) return "GET";
        if (annotation.contains("POST")) return "POST";
        if (annotation.contains("PUT")) return "PUT";
        if (annotation.contains("DELETE")) return "DELETE";
        return null;
    }
}
