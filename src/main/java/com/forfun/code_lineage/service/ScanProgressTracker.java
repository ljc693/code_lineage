package com.forfun.code_lineage.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-file scan progress for CI/CD integration.
 * Updated by ScanOrchestrator at each stage and per-file during analysis.
 */
@Component
public class ScanProgressTracker {

    private final Map<String, Progress> progressMap = new ConcurrentHashMap<>();
    // Retain completed scans for 1 hour after completion
    private final Map<String, Progress> completedMap = new ConcurrentHashMap<>();

    public enum Stage {
        PENDING, FETCHING, ANALYZING, SQL_PARSING, WRITING_GRAPH, COMPLETED, FAILED
    }

    public void start(String traceId, String appId, int totalFiles) {
        Progress p = new Progress(traceId, appId, totalFiles);
        p.stage = Stage.FETCHING;
        progressMap.put(traceId, p);
    }

    public void updateStage(String traceId, Stage stage, int totalFiles) {
        Progress p = progressMap.get(traceId);
        if (p != null) {
            long now = System.currentTimeMillis();
            if (p.stage != null) {
                p.stageTimings.put(p.stage, now - p.stageStartTime);
            }
            p.stage = stage;
            p.stageStartTime = now;
            if (totalFiles > 0) p.totalFiles = totalFiles;
        }
    }

    public void updateFileProgress(String traceId, String fileName, int currentFile, int totalFiles) {
        Progress p = progressMap.get(traceId);
        if (p != null) {
            p.currentFile = fileName;
            p.analyzedFiles = currentFile;
            if (totalFiles > 0) p.totalFiles = totalFiles;
            p.lastUpdate = System.currentTimeMillis();
        }
    }

    public void updateStats(String traceId, int methods, int relations, int sqlRelations) {
        Progress p = progressMap.get(traceId);
        if (p != null) {
            p.methods += methods;
            p.relations += relations;
            p.sqlRelations += sqlRelations;
            p.lastUpdate = System.currentTimeMillis();
        }
    }

    public void complete(String traceId, int methods, int relations, int sqlRelations, int entryPoints, int tables) {
        Progress p = progressMap.get(traceId);
        if (p != null) {
            p.stageTimings.put(p.stage, System.currentTimeMillis() - p.stageStartTime);
            p.stage = Stage.COMPLETED;
            p.completed = true;
            p.methods = methods;
            p.relations = relations;
            p.sqlRelations = sqlRelations;
            p.entryPoints = entryPoints;
            p.tables = tables;
            p.completedAt = System.currentTimeMillis();
            progressMap.remove(traceId);
            completedMap.put(traceId, p);
        }
    }

    public void fail(String traceId, String error) {
        Progress p = progressMap.get(traceId);
        if (p != null) {
            if (p.stage != null) {
                p.stageTimings.put(p.stage, System.currentTimeMillis() - p.stageStartTime);
            }
            p.stage = Stage.FAILED;
            p.completed = true;
            p.error = error;
            p.completedAt = System.currentTimeMillis();
            progressMap.remove(traceId);
            completedMap.put(traceId, p);
        }
    }

    public Progress get(String traceId) {
        Progress p = progressMap.get(traceId);
        return p != null ? p : completedMap.get(traceId);
    }

    public List<Progress> getAllActive() {
        List<Progress> all = new ArrayList<>(progressMap.values());
        all.sort(Comparator.comparing(p -> p.startTime));
        return all;
    }

    public List<Progress> getHistory(String appId) {
        return completedMap.values().stream()
                .filter(p -> appId == null || p.appId.equals(appId))
                .sorted(Comparator.comparing(p -> p.startTime, Comparator.reverseOrder()))
                .toList();
    }

    public static class Progress {
        public final String traceId;
        public final String appId;
        public Stage stage;
        public int totalFiles;
        public int analyzedFiles;
        public String currentFile;
        public int methods;
        public int relations;
        public int sqlRelations;
        public int entryPoints;
        public int tables;
        public long startTime;
        public long stageStartTime;
        public long completedAt;
        public long lastUpdate;
        public boolean completed;
        public String error;
        public final Map<Stage, Long> stageTimings = new LinkedHashMap<>();

        Progress(String traceId, String appId, int totalFiles) {
            this.traceId = traceId;
            this.appId = appId;
            this.totalFiles = totalFiles;
            this.startTime = System.currentTimeMillis();
            this.stageStartTime = this.startTime;
            this.lastUpdate = this.startTime;
        }

        public int getPercent() {
            if (stage == Stage.COMPLETED) return 100;
            if (stage == Stage.FAILED) return 100;
            if (totalFiles == 0) {
                // For non-analyze stages, approximate
                return switch (stage) {
                    case PENDING -> 0;
                    case FETCHING -> 5;
                    case ANALYZING -> 50;
                    case SQL_PARSING -> 80;
                    case WRITING_GRAPH -> 95;
                    default -> 0;
                };
            }
            return Math.min(99, analyzedFiles * 100 / totalFiles);
        }

        public long getElapsedMs() {
            return (completed ? completedAt : System.currentTimeMillis()) - startTime;
        }
    }
}
