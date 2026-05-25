package com.forfun.codel_ineage.analyzer.detector;

import java.util.*;

/**
 * Aggregates all EntryPointDetector implementations.
 * Detectors can be added via constructor (Spring) or programmatically.
 */
public class CompositeEntryPointDetector {

    private final List<EntryPointDetector> detectors;

    public CompositeEntryPointDetector(List<EntryPointDetector> detectors) {
        this.detectors = new ArrayList<>(detectors);
    }

    public void addDetector(EntryPointDetector detector) {
        detectors.add(detector);
    }

    /**
     * Detect entry points by running all registered detectors.
     */
    public List<EntryPointInfo> detect(List<String> classAnnotations,
                                       List<String> methodAnnotations,
                                       String className,
                                       String methodName) {
        List<EntryPointInfo> results = new ArrayList<>();
        for (EntryPointDetector detector : detectors) {
            results.addAll(detector.detect(classAnnotations, methodAnnotations, className, methodName));
        }
        return results;
    }

    public List<EntryPointDetector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }
}
