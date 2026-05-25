package com.forfun.codel_ineage.analyzer.detector;

import java.util.List;

/**
 * SPI interface for detecting service entry points.
 * Each framework (Spring MVC, Dubbo, gRPC, etc.) provides its own implementation.
 * Follows the same Adapter pattern as GraphAdapter and EventBus.
 */
public interface EntryPointDetector {

    /** Framework identifier (e.g., "Spring", "Dubbo", "gRPC", "ShenYu") */
    String framework();

    /**
     * Detect entry points on a method.
     * @param classAnnotations annotations on the containing class
     * @param methodAnnotations annotations on the method itself
     * @param className fully qualified class name
     * @param methodName method name
     * @return detected entry points (empty list if none)
     */
    List<EntryPointInfo> detect(List<String> classAnnotations,
                                List<String> methodAnnotations,
                                String className,
                                String methodName);
}
